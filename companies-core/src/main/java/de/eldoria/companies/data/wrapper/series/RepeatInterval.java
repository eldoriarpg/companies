/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.series;

import net.kyori.adventure.translation.Translatable;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

/**
 * How often a repeating order posts a copy. Intervals are added to local times, so a daily copy keeps its wall-clock
 * time across daylight saving changes.
 */
public enum RepeatInterval implements Translatable {
    /**
     * Every day.
     */
    DAILY(0, 1),
    /**
     * Every week.
     */
    WEEKLY(1, 7);

    private final int id;
    private final int days;

    RepeatInterval(int id, int days) {
        this.id = id;
        this.days = days;
    }

    /**
     * The interval with the given id.
     *
     * @param id stored id
     * @return interval, or empty when no interval has this id
     */
    public static Optional<RepeatInterval> byId(int id) {
        for (var value : values()) if (value.id == id) return Optional.of(value);
        return Optional.empty();
    }

    /**
     * The stable id, as stored in the database.
     *
     * @return id
     */
    public int id() {
        return id;
    }

    /**
     * The point in time one interval later.
     *
     * @param time start
     * @return start plus one interval
     */
    public LocalDateTime next(LocalDateTime time) {
        return time.plusDays(days);
    }

    /**
     * The next due time after now, keeping the original schedule. Missed intervals are skipped, not caught up.
     *
     * @param due the due time that is posted now
     * @param now current time
     * @return the first time of the schedule after now
     */
    public LocalDateTime nextAfter(LocalDateTime due, LocalDateTime now) {
        var next = next(due);
        if (next.isAfter(now)) return next;
        var missed = ChronoUnit.DAYS.between(next, now) / days;
        next = next.plusDays(missed * days);
        while (!next.isAfter(now)) next = next(next);
        return next;
    }

    @Override
    public @NotNull String translationKey() {
        return "enums.repeatInterval." + name().toLowerCase(Locale.ROOT);
    }
}
