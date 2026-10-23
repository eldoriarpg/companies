/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.placeholders;

import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.components.ranking.TopOrder;
import de.eldoria.companies.data.wrapper.company.CompanyRank;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaderboardCacheTest {
    private static List<CompanyRank> ranking(int companies) {
        List<CompanyRank> ranks = new ArrayList<>();
        for (var i = 1; i <= companies; i++) {
            ranks.add(new CompanyRank(i, 100 + i, "C" + i, LocalDateTime.MIN, 1, 1, 1, 1, 1, null, null));
        }
        return ranks;
    }

    @Test
    void aChangedSizeAppliesOnTheNextRefresh() {
        var size = new AtomicInteger(2);
        var cache = new LeaderboardCache((order, range) -> CompletableFuture.completedFuture(ranking(5)), size::get);
        cache.refresh().join();
        assertTrue(cache.at(TopOrder.EARNED, TimeRange.ALL, 3).isEmpty());

        size.set(4);
        cache.refresh().join();

        assertEquals(
                "C4", cache.at(TopOrder.EARNED, TimeRange.ALL, 4).orElseThrow().name());
    }

    @Test
    void theLeaderboardKeepsItsSizeAndEveryPlace() {
        var cache = new LeaderboardCache((order, range) -> CompletableFuture.completedFuture(ranking(5)), 3);

        cache.refresh().join();

        assertEquals(
                "C3",
                cache.at(TopOrder.EARNED, TimeRange.LAST_7_DAYS, 3)
                        .orElseThrow()
                        .name());
        assertTrue(cache.at(TopOrder.EARNED, TimeRange.LAST_7_DAYS, 4).isEmpty());
        assertTrue(cache.at(TopOrder.EARNED, TimeRange.LAST_7_DAYS, 0).isEmpty());
        assertEquals(OptionalInt.of(5), cache.place(105, TopOrder.EARNED, TimeRange.LAST_7_DAYS));
        assertEquals(OptionalInt.empty(), cache.place(999, TopOrder.EARNED, TimeRange.LAST_7_DAYS));
    }

    @Test
    void everyOrderingAndRangeIsLoaded() {
        List<String> loaded = new ArrayList<>();
        var cache = new LeaderboardCache(
                (order, range) -> {
                    loaded.add(order + "/" + range);
                    return CompletableFuture.completedFuture(List.of());
                },
                3);

        assertFalse(cache.loaded(TopOrder.LEVEL, TimeRange.ALL));
        cache.refresh().join();

        assertEquals(TopOrder.values().length * TimeRange.values().length, loaded.size());
        assertTrue(cache.loaded(TopOrder.LEVEL, TimeRange.ALL));
    }

    @Test
    void aFailedLoadKeepsThePreviousRanking() {
        var fail = new AtomicBoolean();
        var cache = new LeaderboardCache(
                (order, range) -> fail.get()
                        ? CompletableFuture.failedFuture(new IllegalStateException())
                        : CompletableFuture.completedFuture(ranking(2)),
                3);
        cache.refresh().join();
        fail.set(true);

        cache.refresh().join();

        assertEquals(
                "C2", cache.at(TopOrder.ORDERS, TimeRange.ALL, 2).orElseThrow().name());
    }
}
