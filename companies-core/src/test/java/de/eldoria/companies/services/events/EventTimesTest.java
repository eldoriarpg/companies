/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventTimesTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void durationsAreDaysHoursAndMinutes() {
        assertEquals(Optional.of(Duration.ofHours(48)), EventTimes.duration("48h"));
        assertEquals(Optional.of(Duration.ofHours(36).plusMinutes(30)), EventTimes.duration("1d12h30m"));
        assertEquals(Optional.of(Duration.ofMinutes(90)), EventTimes.duration(" 90M "));
        assertTrue(EventTimes.duration("0h").isEmpty());
        assertTrue(EventTimes.duration("soon").isEmpty());
        assertTrue(EventTimes.duration("").isEmpty());
    }

    @Test
    void datesAreReadInTheServersZone() {
        assertEquals(Optional.of(LocalDateTime.of(2026, 12, 24, 17, 0)), EventTimes.date("2026-12-24 18:00", BERLIN));
        assertEquals(Optional.of(LocalDateTime.of(2026, 7, 1, 16, 0)), EventTimes.date("2026-07-01 18:00", BERLIN));
        assertTrue(EventTimes.date("24.12.2026", BERLIN).isEmpty());
        assertEquals("2026-12-24 18:00", EventTimes.show(LocalDateTime.of(2026, 12, 24, 17, 0), BERLIN));
    }

    @Test
    void aWeeklyStartKeepsTheServersClockAcrossTheChangeOfTime() {
        var weekly = EventTimes.weekly("saturday 18:00").orElseThrow();
        assertEquals(new EventTimes.Weekly(DayOfWeek.SATURDAY, LocalTime.of(18, 0)), weekly);

        var summer = weekly.next(LocalDateTime.of(2026, 10, 20, 0, 0), BERLIN);
        var winter = weekly.next(summer.plusSeconds(1), BERLIN);

        assertEquals(LocalDateTime.of(2026, 10, 24, 16, 0), summer);
        assertEquals(LocalDateTime.of(2026, 10, 31, 17, 0), winter);
    }

    @Test
    void aWeeklyStartAtItsOwnTimeIsThatStartAndOneJustAfterIsTheNextWeek() {
        var weekly = EventTimes.weekly("FRIDAY 20:30").orElseThrow();
        var start = LocalDateTime.of(2026, 10, 30, 19, 30);

        assertEquals(start, weekly.next(start, BERLIN));
        assertEquals(start.plusWeeks(1), weekly.next(start.plusSeconds(1), BERLIN));
    }

    @Test
    void aStartTheClocksSkipMovesByTheGap() {
        var weekly = EventTimes.weekly("SUNDAY 02:30").orElseThrow();

        assertEquals(LocalDateTime.of(2027, 3, 28, 1, 30), weekly.next(LocalDateTime.of(2027, 3, 27, 0, 0), BERLIN));
    }

    @Test
    void anUnreadableWeeklyStartIsNone() {
        assertTrue(EventTimes.weekly("SATURDAY").isEmpty());
        assertTrue(EventTimes.weekly("SAMSTAG 18:00").isEmpty());
        assertTrue(EventTimes.weekly("SATURDAY 25:00").isEmpty());
    }
}
