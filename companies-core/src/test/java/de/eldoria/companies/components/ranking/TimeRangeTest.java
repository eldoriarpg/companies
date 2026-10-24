/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.ranking;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

class TimeRangeTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 3, 14, 15, 16);

    @Test
    void allTimeHasNoStart() {
        Assertions.assertEquals(Optional.empty(), TimeRange.ALL.start(NOW));
    }

    @Test
    void windowsRollBackFromNowAcrossTheYearBoundary() {
        Assertions.assertEquals(
                Optional.of(LocalDateTime.of(2025, 12, 27, 14, 15, 16)), TimeRange.LAST_7_DAYS.start(NOW));
        Assertions.assertEquals(
                Optional.of(LocalDateTime.of(2025, 12, 4, 14, 15, 16)), TimeRange.LAST_30_DAYS.start(NOW));
    }

    @Test
    void windowsDoNotSnapToCalendarPeriods() {
        var monday = LocalDateTime.of(2026, 3, 2, 0, 0);

        Assertions.assertEquals(Optional.of(LocalDateTime.of(2026, 2, 23, 0, 0)), TimeRange.LAST_7_DAYS.start(monday));
        Assertions.assertEquals(Optional.of(LocalDateTime.of(2026, 1, 31, 0, 0)), TimeRange.LAST_30_DAYS.start(monday));
    }

    @Test
    void rangesAreFoundByIdInAnyCase() {
        Assertions.assertEquals(Optional.of(TimeRange.LAST_30_DAYS), TimeRange.byId("30D"));
        Assertions.assertEquals(Optional.of(TimeRange.ALL), TimeRange.byId("all"));
        Assertions.assertEquals(Optional.empty(), TimeRange.byId("month"));
        Assertions.assertEquals(
                TimeRange.values().length,
                Arrays.stream(TimeRange.values()).map(TimeRange::id).distinct().count());
    }
}
