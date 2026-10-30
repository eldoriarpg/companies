/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads and computes the times of server events as admins write them: durations such as {@code 48h} or
 * {@code 1d12h}, dates such as {@code 2026-12-24 18:00} and weekly starts such as {@code SATURDAY 18:00}, the dates
 * and weekly starts in the server's time zone. Events store their times in UTC.
 */
public final class EventTimes {
    /**
     * How dates are written, in the server's time zone.
     */
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Pattern DURATION = Pattern.compile("(?:(\\d{1,5})d)?(?:(\\d{1,6})h)?(?:(\\d{1,7})m)?");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");

    private EventTimes() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @param text a duration of days, hours and minutes, such as {@code 48h}, {@code 2d} or {@code 1d12h30m}
     * @return the duration, empty when the text is none or it is zero
     */
    public static Optional<Duration> duration(String text) {
        var matcher = DURATION.matcher(text.strip().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) return Optional.empty();
        var duration = Duration.ZERO;
        if (matcher.group(1) != null) duration = duration.plusDays(Long.parseLong(matcher.group(1)));
        if (matcher.group(2) != null) duration = duration.plusHours(Long.parseLong(matcher.group(2)));
        if (matcher.group(3) != null) duration = duration.plusMinutes(Long.parseLong(matcher.group(3)));
        return duration.isZero() ? Optional.empty() : Optional.of(duration);
    }

    /**
     * @param text a date in the server's time zone, such as {@code 2026-12-24 18:00}
     * @param zone the server's time zone
     * @return the date in UTC, empty when the text is no date
     */
    public static Optional<LocalDateTime> date(String text, ZoneId zone) {
        try {
            return Optional.of(utc(LocalDateTime.parse(text.strip(), DATE), zone));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * @param utc  a time in UTC
     * @param zone the server's time zone
     * @return the time as the server shows it, such as {@code 2026-12-24 18:00}
     */
    public static String show(LocalDateTime utc, ZoneId zone) {
        return DATE.format(utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone));
    }

    /**
     * @param text a weekly start, a day of the week and a time, such as {@code SATURDAY 18:00}
     * @return the weekly start, empty when the text is none
     */
    public static Optional<Weekly> weekly(String text) {
        var parts = text.strip().split("\\s+");
        if (parts.length != 2) return Optional.empty();
        try {
            var day = DayOfWeek.valueOf(parts[0].toUpperCase(Locale.ROOT));
            var time = LocalTime.parse(parts[1], TIME);
            return Optional.of(new Weekly(day, time));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * Turns a time of the server's zone into UTC. A time that does not exist, since the clocks were put forward, is
     * moved by the length of the gap; of a time that exists twice, since the clocks were put back, the earlier is
     * taken.
     *
     * @param local a time in the server's time zone
     * @param zone  the server's time zone
     * @return the time in UTC
     */
    public static LocalDateTime utc(LocalDateTime local, ZoneId zone) {
        return local.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /**
     * A start every week, at a day and a time of the server's time zone.
     *
     * @param day  the day of the week
     * @param time the time of the day
     */
    public record Weekly(DayOfWeek day, LocalTime time) {
        /**
         * The first start at or after a time.
         *
         * @param from a time in UTC
         * @param zone the server's time zone
         * @return the start in UTC
         */
        public LocalDateTime next(LocalDateTime from, ZoneId zone) {
            var local = from.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone);
            var date = local.toLocalDate().with(TemporalAdjusters.nextOrSame(day));
            var start = utc(date.atTime(time), zone);
            if (start.isBefore(from)) start = utc(date.plusWeeks(1).atTime(time), zone);
            return start.truncatedTo(ChronoUnit.SECONDS);
        }
    }
}
