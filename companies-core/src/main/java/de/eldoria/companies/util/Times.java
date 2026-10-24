/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import de.eldoria.companies.components.ranking.TimeRange;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The plugin's times. Every stored {@link LocalDateTime} means UTC, taken from {@link #UTC}, so all nodes and databases
 * agree on an instant. The server's zone, {@code timezone} of {@code config.yml}, decides how a time is shown and where
 * a day ends; it is set when the configuration loads and is the machine's zone until then.
 */
public final class Times {
    /**
     * The clock every stored time comes from.
     */
    public static final Clock UTC = Clock.systemUTC();

    private static volatile Supplier<ZoneId> zone = Times::machineZone;

    private Times() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @return the current time in UTC, as it is stored
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(UTC);
    }

    /**
     * The zone of the machine the server runs on: the fallback of the server's zone, and the zone times were stored in
     * before they were stored in UTC.
     *
     * @return the machine's zone
     */
    public static ZoneId machineZone() {
        return ZoneId.systemDefault();
    }

    /**
     * @return the server's zone
     */
    public static ZoneId zone() {
        return zone.get();
    }

    /**
     * Sets where the server's zone comes from, read again each time so a reload applies at once.
     *
     * @param serverZone the zone
     */
    public static void useZone(Supplier<ZoneId> serverZone) {
        zone = serverZone;
    }

    /**
     * A stored time as the server shows it.
     *
     * @param utc a stored time
     * @return the time in the server's zone
     */
    public static ZonedDateTime local(LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone());
    }

    /**
     * @return today in the server's zone
     */
    public static LocalDate today() {
        return LocalDate.now(UTC.withZone(zone()));
    }

    /**
     * Where a day of the server's calendar starts, as a stored time.
     *
     * @param day the day
     * @return its first instant in UTC
     */
    public static LocalDateTime startOfDay(LocalDate day) {
        return day.atStartOfDay(zone()).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /**
     * Where a statistics range starts: at midnight of the server's calendar, so the last seven days are today and the
     * six days before.
     *
     * @param range the range
     * @param now   the current time, as stored
     * @return its first instant in UTC, empty for a range without start
     */
    public static Optional<LocalDateTime> start(TimeRange range, LocalDateTime now) {
        return range.start(local(now).toLocalDate().atStartOfDay())
                .map(start -> startOfDay(start.toLocalDate().plusDays(1)));
    }
}
