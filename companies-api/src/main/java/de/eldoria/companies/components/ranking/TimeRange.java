/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.ranking;

import net.kyori.adventure.translation.Translatable;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * The time a ranking or a statistic covers. The ranged values are rolling windows ending now, not calendar periods.
 * Only companies with activity in a window are ranked in it.
 */
public enum TimeRange implements Translatable {
    /**
     * Everything since the company was founded.
     */
    ALL("all", 0),
    /**
     * The last 30 days.
     */
    LAST_30_DAYS("30d", 30),
    /**
     * The last 7 days.
     */
    LAST_7_DAYS("7d", 7);

    private final String id;
    private final int days;

    TimeRange(String id, int days) {
        this.id = id;
        this.days = days;
    }

    /**
     * The range with the given id.
     *
     * @param id id as returned by {@link #id()}, in any case
     * @return range, or empty when no range has this id
     */
    public static Optional<TimeRange> byId(String id) {
        for (var range : values()) if (range.id.equalsIgnoreCase(id)) return Optional.of(range);
        return Optional.empty();
    }

    /**
     * The short id used in commands and placeholders: {@code all}, {@code 30d} or {@code 7d}.
     *
     * @return id
     */
    public String id() {
        return id;
    }

    /**
     * The first moment of the range.
     *
     * @param now current time
     * @return the start, or empty for {@link #ALL}
     */
    public Optional<LocalDateTime> start(LocalDateTime now) {
        if (this == ALL) return Optional.empty();
        return Optional.of(now.minusDays(days));
    }

    @Override
    public @NotNull String translationKey() {
        return "enums.timeRange." + name().toLowerCase(Locale.ROOT);
    }
}
