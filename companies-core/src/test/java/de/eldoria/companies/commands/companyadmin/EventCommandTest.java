/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.components.event.EventKind;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteServerEventData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.menus.admin.EventForm;
import de.eldoria.companies.menus.admin.EventForm.Field;
import de.eldoria.companies.services.events.ServerEventService;
import de.eldoria.companies.services.events.ServerEventService.Change;
import de.eldoria.companies.util.AdminLog;
import de.eldoria.companies.util.Times;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventCommandTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @TempDir
    Path directory;

    private final List<String> logged = new CopyOnWriteArrayList<>();
    private final List<String> replies = new CopyOnWriteArrayList<>();
    private AdminLog log;
    private ServerEventService service;
    private LocalDateTime now;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        log = new AdminLog(logger);
        service = new ServerEventService(
                new SqLiteServerEventData(), name -> name.toUpperCase(Locale.ROOT), signal -> {}, Times.UTC, logger);
        now = LocalDateTime.now(Times.UTC).withNano(0);
    }

    @Test
    void aValidFormCreatesTheEventAndIsLogged() {
        var form = EventForm.of("Harvest")
                .with(Field.KIND, "REWARD_MULTIPLIER")
                .with(Field.VALUE, "1.5")
                .with(Field.MATERIALS, "wheat")
                .with(Field.FUNDING, "MINTED")
                .with(Field.BUDGET, "500");

        var saved = assertInstanceOf(EventCommand.Saved.Created.class, save(form));

        assertEquals(List.of("WHEAT"), saved.event().materials());
        assertEquals(
                1,
                logged.stream()
                        .filter(line -> line.startsWith("[admin] Nora event create id=" + saved.id())
                                && line.contains("materials=WHEAT")
                                && line.contains("budget=500.0"))
                        .count());
    }

    @Test
    void anInvalidFieldOrARefusedEventCreatesNothingAndNamesWhy() {
        var invalid = save(EventForm.of("Harvest").with(Field.KIND, "BOOST"));
        var refused = save(EventForm.of("Harvest")
                .with(Field.KIND, "REWARD_MULTIPLIER")
                .with(Field.VALUE, "1.5")
                .with(Field.FUNDING, "MINTED"));

        assertEquals(new EventCommand.Saved.Refused("companyadmin.event.error.kind"), invalid);
        assertEquals(new EventCommand.Saved.Refused("companyadmin.event.problem.BUDGET"), refused);
        assertTrue(logged.isEmpty());
    }

    @Test
    void everyChangeIsAnsweredAndOnlyADoneOneIsLogged() {
        var answers = Map.of(
                Change.DONE, "message:companyadmin.event.done.extend",
                Change.UNKNOWN, "error:companyadmin.event.error.unknown",
                Change.CLOSED, "error:companyadmin.event.error.closed",
                Change.REFUSED, "error:companyadmin.event.error.refused.extend",
                Change.FAILED, "error:companyadmin.event.error.failed");
        for (var entry : answers.entrySet()) {
            replies.clear();
            EventCommand.changed(log, recorder(), "Nora", 7, "extend", entry.getKey(), Map.of("by", "12h"));
            assertEquals(List.of(entry.getValue()), replies, entry.getKey().name());
        }

        assertEquals(List.of("[admin] Nora event extend id=7 by=12h"), logged);
    }

    @Test
    void endCancelExtendAndBudgetAnswerWhatTheServiceDid() {
        var id = service.create(ServerEvent.draft(
                        "Harvest",
                        EventKind.REWARD_MULTIPLIER,
                        2,
                        List.of(),
                        List.of(),
                        now,
                        now.plusHours(2),
                        SourceType.MINTED,
                        null,
                        100.0,
                        null,
                        "Nora",
                        now))
                .id();
        service.run();

        changed(id, "budget", service.budget(id, 200.0));
        changed(id, "extend", service.extend(id, Duration.ofHours(1)));
        changed(id, "end", service.end(id, "Nora"));
        changed(id, "cancel", service.cancel(id, "Nora"));

        assertEquals(
                List.of(
                        "message:companyadmin.event.done.budget",
                        "message:companyadmin.event.done.extend",
                        "message:companyadmin.event.done.end",
                        "error:companyadmin.event.error.closed"),
                replies);
        assertEquals(3, logged.size());
    }

    @Test
    void theMarkersOfWhoClosedAnEventAreNamedInWords() {
        assertTrue(EventCommand.closedBy(ServerEvent.MISSED_BY).build().contains("closedBy.missed"));
        assertTrue(EventCommand.closedBy(ServerEvent.TEMPLATE_BY).build().contains("closedBy.template"));
        assertTrue(EventCommand.closedBy(null).build().contains("closedBy.time"));
        assertTrue(EventCommand.closedBy("Nora").build().contains("closedBy.admin"));
    }

    @Test
    void aBudgetIsAnAmountAboveZeroOrNone() {
        assertEquals(Optional.of(Optional.of(250.5)), EventCommand.budgetOf("250,5"));
        assertEquals(Optional.of(Optional.empty()), EventCommand.budgetOf("NONE"));
        assertEquals(Optional.empty(), EventCommand.budgetOf("0"));
        assertEquals(Optional.empty(), EventCommand.budgetOf("-3"));
        assertEquals(Optional.empty(), EventCommand.budgetOf("lots"));
    }

    private EventCommand.Saved save(EventForm form) {
        return EventCommand.save(service, log, "Nora", form, BERLIN, now, "Collector");
    }

    private void changed(int id, String action, Change change) {
        EventCommand.changed(log, recorder(), "Nora", id, action, change, Map.of());
    }

    private EventCommand.Replies recorder() {
        return new EventCommand.Replies() {
            @Override
            public void message(String key, TagResolver.Single... values) {
                replies.add("message:" + key);
            }

            @Override
            public void error(String key, TagResolver.Single... values) {
                replies.add("error:" + key);
            }
        };
    }
}
