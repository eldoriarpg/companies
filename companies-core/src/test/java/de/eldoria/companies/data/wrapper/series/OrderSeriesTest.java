/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.series;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

class OrderSeriesTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 28, 9, 0, 0);

    private static OrderSeries series(OptionalInt maxCopies, Optional<LocalDateTime> ends) {
        return OrderSeries.draft(UUID.randomUUID(), "Iron", List.of(), RepeatInterval.DAILY, maxCopies, ends, 5, NOW);
    }

    @Test
    void theNextDueTimeKeepsTheWallClockTime() {
        Assertions.assertEquals(LocalDateTime.of(2026, 3, 29, 9, 0, 0), RepeatInterval.DAILY.next(NOW));
        Assertions.assertEquals(LocalDateTime.of(2026, 4, 4, 9, 0, 0), RepeatInterval.WEEKLY.next(NOW));
    }

    @Test
    void missedIntervalsAreSkippedNotCaughtUp() {
        var now = NOW.plusDays(3).plusHours(2);

        Assertions.assertEquals(LocalDateTime.of(2026, 4, 1, 9, 0, 0), RepeatInterval.DAILY.nextAfter(NOW, now));
        Assertions.assertEquals(LocalDateTime.of(2026, 4, 4, 9, 0, 0), RepeatInterval.WEEKLY.nextAfter(NOW, now));
        Assertions.assertEquals(LocalDateTime.of(2026, 3, 29, 9, 0, 0), RepeatInterval.DAILY.nextAfter(NOW, NOW));
        Assertions.assertEquals(
                LocalDateTime.of(2026, 3, 30, 9, 0, 0),
                RepeatInterval.DAILY.nextAfter(NOW, LocalDateTime.of(2026, 3, 29, 9, 0, 0)));
    }

    @Test
    void theEndIsReachedByCountOrDate() {
        var counted = series(OptionalInt.of(2), Optional.empty());
        var dated = series(OptionalInt.empty(), Optional.of(NOW.plusDays(2)));

        Assertions.assertFalse(counted.endReached(NOW));
        Assertions.assertTrue(new OrderSeries(
                        1,
                        counted.owner(),
                        counted.name(),
                        counted.lines(),
                        counted.interval(),
                        counted.maxCopies(),
                        counted.ends(),
                        2,
                        counted.nextDue(),
                        counted.currentOrder(),
                        counted.state(),
                        counted.stopReason(),
                        counted.waiting(),
                        counted.orderLimit(),
                        counted.created())
                .endReached(NOW));
        Assertions.assertFalse(dated.endReached(NOW.plusDays(2)));
        Assertions.assertTrue(dated.endReached(NOW.plusDays(2).plusSeconds(1)));
    }

    @Test
    void idsAreStable() {
        for (var value : RepeatInterval.values()) {
            Assertions.assertEquals(value, RepeatInterval.byId(value.id()).orElseThrow());
        }
        for (var value : SeriesState.values()) {
            Assertions.assertEquals(value, SeriesState.byId(value.id()).orElseThrow());
        }
        for (var value : SeriesStopReason.values()) {
            Assertions.assertEquals(value, SeriesStopReason.byId(value.id()).orElseThrow());
        }
        for (var value : SeriesWait.values()) {
            Assertions.assertEquals(value, SeriesWait.byId(value.id()).orElseThrow());
        }
        Assertions.assertEquals(4, SeriesStopReason.INVALID.id());
        Assertions.assertTrue(SeriesState.byId(9).isEmpty());
        Assertions.assertEquals("enums.seriesStopReason.cannot_pay", SeriesStopReason.CANNOT_PAY.translationKey());
    }
}
