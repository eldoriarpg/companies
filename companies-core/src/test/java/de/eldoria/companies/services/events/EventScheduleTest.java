/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import de.eldoria.companies.configuration.elements.events.EventSettings;
import de.eldoria.companies.configuration.elements.events.EventTemplate;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteServerEventData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.event.EventKind;
import de.eldoria.companies.data.wrapper.event.EventState;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventScheduleTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final LocalDateTime SATURDAY = LocalDateTime.of(2026, 10, 31, 17, 0);

    @TempDir
    Path directory;

    private final EventSettings settings = new EventSettings();
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private SqLiteServerEventData data;
    private ZoneId zone = BERLIN;
    private boolean readable = true;
    private EventSchedule schedule;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        data = new SqLiteServerEventData();
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                warnings.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        schedule = new EventSchedule(() -> settings, data, () -> zone, () -> "Collector", () -> readable, logger);
    }

    @Test
    void aWeeklyTemplateCreatesItsNextEventAheadOfTimeOnce() {
        settings.templates(List.of(weekend()));

        assertTrue(schedule.due(SATURDAY.minusHours(25)).isEmpty());
        var due = schedule.due(SATURDAY.minusHours(23));

        assertEquals(1, due.size());
        var event = due.getFirst();
        assertEquals(SATURDAY, event.starts());
        assertEquals(SATURDAY.plusHours(48), event.ends());
        assertEquals("weekend", event.template());
        assertEquals("Weekend boost", event.name());
        assertEquals(EventSchedule.CREATOR, event.createdBy());
        assertEquals(SourceType.MINTED, event.funding());
        assertEquals(500.0, event.budget());

        store(event);
        assertTrue(schedule.due(SATURDAY.minusHours(1)).isEmpty());
        assertTrue(schedule.due(SATURDAY.plusHours(1)).isEmpty());
    }

    @Test
    void theNextEventFollowsOnceTheLastOneClosedAndKeepsTheServersClock() {
        settings.templates(List.of(weekend()));
        var first = store(schedule.due(SATURDAY.minusHours(1)).getFirst());
        close(first);

        assertTrue(schedule.due(SATURDAY.plusHours(49)).isEmpty());
        var next = schedule.due(SATURDAY.plusDays(7).minusHours(12));

        assertEquals(1, next.size());
        assertEquals(SATURDAY.plusDays(7), next.getFirst().starts());
    }

    @Test
    void anEventCancelledByAnAdminIsNotCreatedAgain() {
        settings.templates(List.of(weekend()));
        var first = store(schedule.due(SATURDAY.minusHours(1)).getFirst());
        data.state(first, EventState.SCHEDULED, EventState.CANCELLED, "Nora", SATURDAY.minusMinutes(30));

        assertTrue(schedule.due(SATURDAY.plusHours(1)).isEmpty());
    }

    @Test
    void aPrimaryComingUpDuringAStartCreatesTheRunningEventWithItsPlannedTimes() {
        settings.templates(List.of(weekend()));

        var due = schedule.due(SATURDAY.plusHours(3));

        assertEquals(1, due.size());
        assertEquals(SATURDAY, due.getFirst().starts());
        assertEquals(SATURDAY.plusHours(48), due.getFirst().ends());
    }

    @Test
    void aStartWhoseWholeTimePassedWhileThePrimaryWasDownIsSkipped() {
        settings.templates(List.of(weekend()));
        close(store(schedule.due(SATURDAY.minusHours(1)).getFirst()));

        var due = schedule.due(SATURDAY.plusDays(21).minusHours(2));

        assertEquals(1, due.size());
        assertEquals(LocalDateTime.of(2026, 11, 21, 17, 0), due.getFirst().starts());
    }

    @Test
    void fixedDatesAreCreatedOneAfterTheOther() {
        var christmas = new EventTemplate("christmas", EventKind.TAX_REDUCTION, 5);
        christmas.dates(List.of("2026-12-31 18:00", "2026-12-24 18:00"));
        christmas.duration("1d");
        settings.templates(List.of(christmas));
        var eve = LocalDateTime.of(2026, 12, 24, 17, 0);

        var first = schedule.due(eve.minusHours(2));
        assertEquals(eve, first.getFirst().starts());
        assertEquals(eve.plusDays(1), first.getFirst().ends());
        close(store(first.getFirst()));

        assertEquals(
                eve.plusDays(7),
                schedule.due(eve.plusDays(7).minusHours(1)).getFirst().starts());
        close(store(schedule.due(eve.plusDays(7).minusHours(1)).getFirst()));
        assertTrue(schedule.due(eve.plusDays(30)).isEmpty());
    }

    @Test
    void aMultiplierFundedFromAnAccountWithoutOneIsPaidByTheCollector() {
        var bonus = new EventTemplate("bonus", EventKind.REWARD_MULTIPLIER, 1.5);
        bonus.every("SATURDAY 18:00");
        bonus.funding(SourceType.ACCOUNT);
        settings.templates(List.of(bonus));

        var event = schedule.due(SATURDAY).getFirst();

        assertEquals("Collector", event.account());
        assertEquals(null, event.budget());
    }

    @Test
    void aTemplateWithAProblemCreatesNothingAndIsLoggedOnce() {
        var broken = new EventTemplate("broken", EventKind.TAX_REDUCTION, 5);
        broken.every("FUNDAY 18:00");
        var off = weekend();
        off.enabled(false);
        settings.templates(List.of(broken, off, weekend(), weekend()));

        assertEquals(
                0,
                schedule.due(SATURDAY.minusHours(1)).stream()
                        .filter(event -> !"weekend".equals(event.template()))
                        .count());
        schedule.due(SATURDAY.minusHours(1));

        assertEquals(
                1, warnings.stream().filter(line -> line.contains("broken")).count());
        assertEquals(
                1,
                warnings.stream()
                        .filter(line -> line.contains("earlier template"))
                        .count());
    }

    @Test
    void aRefusedEventIsLoggedOnce() {
        var draft = weekendEvent();

        schedule.refused(draft, ServerEvent.Problem.MATERIALS);
        schedule.refused(draft, ServerEvent.Problem.MATERIALS);

        assertEquals(1, warnings.size());
    }

    @Test
    void aChangedStartNeverCreatesASecondEventInTheTimeOfTheLastOne() {
        settings.templates(List.of(weekend()));
        var first = store(schedule.due(SATURDAY.minusHours(1)).getFirst());
        data.state(first, EventState.SCHEDULED, EventState.ACTIVE, null, SATURDAY);
        data.state(first, EventState.ACTIVE, EventState.ENDED, "Nora", SATURDAY.plusHours(2));
        var sunday = weekend();
        sunday.every("SUNDAY 18:00");
        settings.templates(List.of(sunday));

        assertTrue(schedule.due(SATURDAY.plusHours(23)).isEmpty());
        var next = schedule.due(SATURDAY.plusDays(8).minusHours(1));
        assertEquals(SATURDAY.plusDays(8), next.getFirst().starts());
    }

    @Test
    void aChangedTimeZoneNeverCreatesASecondEventInTheTimeOfTheLastOne() {
        settings.templates(List.of(weekend()));
        close(store(schedule.due(SATURDAY.minusHours(1)).getFirst()));
        zone = ZoneId.of("America/New_York");

        assertTrue(schedule.due(SATURDAY.plusHours(4)).isEmpty());
        var next = schedule.due(SATURDAY.plusDays(7));
        assertEquals(LocalDateTime.of(2026, 11, 7, 23, 0), next.getFirst().starts());
    }

    @Test
    void aRenamedTemplateIsANewTemplateAndTheOldIdIsNotKept() {
        settings.templates(List.of(weekend()));
        var old = data.event(store(schedule.due(SATURDAY.minusHours(1)).getFirst()))
                .orElseThrow();
        var renamed = weekend();
        renamed.name("Weekend boost");
        var moved = new EventTemplate("weekend-2", EventKind.REWARD_MULTIPLIER, 1.5);
        moved.every("SATURDAY 18:00");
        moved.funding(SourceType.MINTED);
        moved.budget(500);
        settings.templates(List.of(moved));

        assertEquals(false, schedule.keeps(old));
        assertEquals(
                "weekend-2", schedule.due(SATURDAY.minusHours(1)).getFirst().template());
    }

    @Test
    void aDisabledTemplateKeepsNoWaitingEventButEventsMadeByHandAndUnreadableFilesKeepAll() {
        settings.templates(List.of(weekend()));
        var event = data.event(store(schedule.due(SATURDAY.minusHours(1)).getFirst()))
                .orElseThrow();
        assertTrue(schedule.keeps(event));

        var off = weekend();
        off.enabled(false);
        settings.templates(List.of(off));
        assertEquals(false, schedule.keeps(event));
        assertTrue(schedule.keeps(byHand()));

        readable = false;
        assertTrue(schedule.keeps(event));
        settings.templates(List.of(weekend()));
        assertTrue(schedule.due(SATURDAY.plusDays(7)).isEmpty());
    }

    @Test
    void aWeeklyStartThroughTheChangeOfTimeKeepsTheServersClock() {
        settings.templates(List.of(weekend()));
        var summer = LocalDateTime.of(2026, 10, 24, 16, 0);
        var first = schedule.due(summer.minusHours(1)).getFirst();
        assertEquals(summer, first.starts());
        close(store(first));

        assertEquals(SATURDAY, schedule.due(SATURDAY.minusHours(1)).getFirst().starts());
    }

    @Test
    void aRefusalThatComesBackAfterItWasFixedIsLoggedAgain() {
        var draft = weekendEvent();
        schedule.refused(draft, ServerEvent.Problem.MATERIALS);
        schedule.due(SATURDAY);
        schedule.refused(draft, ServerEvent.Problem.MATERIALS);
        schedule.due(SATURDAY);
        schedule.due(SATURDAY);
        schedule.refused(draft, ServerEvent.Problem.MATERIALS);

        assertEquals(2, warnings.size());
    }

    @Test
    void aTemplateEnabledAgainBeforeItsWithdrawnStartCreatesItAgain() {
        settings.templates(List.of(weekend()));
        var first = store(schedule.due(SATURDAY.minusHours(2)).getFirst());
        data.state(first, EventState.SCHEDULED, EventState.CANCELLED, ServerEvent.TEMPLATE_BY, SATURDAY.minusHours(1));

        var again = schedule.due(SATURDAY.minusMinutes(30));

        assertEquals(1, again.size());
        assertEquals(SATURDAY, again.getFirst().starts());
    }

    @Test
    void aDateWithinTheEventOfTheDateBeforeItIsLoggedOnceAndSkipped() {
        var dates = new EventTemplate("dates", EventKind.TAX_REDUCTION, 5);
        dates.dates(List.of("2026-12-24 18:00", "2026-12-25 12:00"));
        dates.duration("1d");
        settings.templates(List.of(dates));
        var eve = LocalDateTime.of(2026, 12, 24, 17, 0);

        close(store(schedule.due(eve.minusHours(1)).getFirst()));
        schedule.due(eve.plusHours(2));

        assertTrue(schedule.due(eve.plusHours(18)).isEmpty());
        assertEquals(
                1,
                warnings.stream()
                        .filter(line -> line.contains("2026-12-25 12:00"))
                        .count());
    }

    private static EventTemplate weekend() {
        var template = new EventTemplate("weekend", EventKind.REWARD_MULTIPLIER, 1.5);
        template.name("Weekend boost");
        template.every("SATURDAY 18:00");
        template.duration("48h");
        template.funding(SourceType.MINTED);
        template.budget(500);
        return template;
    }

    private static ServerEvent weekendEvent() {
        return ServerEvent.draft(
                "Weekend",
                EventKind.TAX_REDUCTION,
                5,
                List.of(),
                List.of(),
                SATURDAY,
                SATURDAY.plusDays(1),
                null,
                null,
                null,
                "weekend",
                EventSchedule.CREATOR,
                SATURDAY);
    }

    private static ServerEvent byHand() {
        return ServerEvent.draft(
                "By hand",
                EventKind.TAX_REDUCTION,
                5,
                List.of(),
                List.of(),
                SATURDAY,
                SATURDAY.plusDays(1),
                null,
                null,
                null,
                null,
                "Nora",
                SATURDAY);
    }

    private int store(ServerEvent event) {
        return data.create(event).orElseThrow();
    }

    private void close(int id) {
        var state = data.event(id).orElseThrow().state();
        data.state(id, state, EventState.ENDED, null, SATURDAY);
    }
}
