/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards;

import de.eldoria.companies.boards.SignBoardRefresher.WriteResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignBoardRefresherTest {
    private static final UUID WORLD = UUID.randomUUID();
    private static final BoardLocation FIRST = new BoardLocation(WORLD, 0, 64, 0);
    private static final BoardLocation SECOND = new BoardLocation(WORLD, 1, 64, 0);
    private static final BoardLocation THIRD = new BoardLocation(WORLD, 2, 64, 0);

    private final Map<List<String>, Integer> counts = new HashMap<>();
    private final List<List<String>> counted = new ArrayList<>();
    private final List<BoardLocation> written = new ArrayList<>();
    private final Map<BoardLocation, WriteResult> results = new HashMap<>();
    private MutableClock clock;
    private RefreshSchedule schedule;
    private SignBoardIndex index;
    private SignBoardRefresher refresher;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        clock = new MutableClock(Instant.parse("2026-10-23T12:00:00Z"));
        schedule = new RefreshSchedule(clock, () -> Duration.ofSeconds(60), Duration.ofSeconds(5));
        index = new SignBoardIndex(new BoardSignData(MockBukkit.createMockPlugin()));
        refresher = new SignBoardRefresher(
                index,
                spec -> spec.equals("unknown")
                        ? Optional.empty()
                        : Optional.of(new BoardTarget(null, BoardFilter.ofMaterials(List.of(spec)), spec)),
                filter -> {
                    counted.add(filter.materials());
                    return CompletableFuture.completedFuture(counts.getOrDefault(filter.materials(), 0));
                },
                (location, target, count) -> {
                    written.add(location);
                    return results.getOrDefault(location, WriteResult.WRITTEN);
                },
                schedule,
                Runnable::run,
                Logger.getLogger("test"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void everyBoardIsCountedOnce() {
        index.put(FIRST, "stone");
        index.put(SECOND, "stone");
        index.put(THIRD, "dirt");

        refresher.refresh().join();

        assertEquals(2, counted.size());
        assertEquals(3, written.size());
    }

    @Test
    void onlyChangedCountsAreWritten() {
        index.put(FIRST, "stone");
        index.put(SECOND, "dirt");
        refresher.refresh().join();
        written.clear();

        counts.put(List.of("stone"), 4);
        refresher.refresh().join();

        assertEquals(List.of(FIRST), written);
    }

    @Test
    void goneSignsLeaveTheIndex() {
        index.put(FIRST, "stone");
        results.put(FIRST, WriteResult.GONE);

        refresher.refresh().join();

        assertEquals(0, index.size());
    }

    @Test
    void unloadedSignsAreWrittenAgainLater() {
        index.put(FIRST, "stone");
        results.put(FIRST, WriteResult.UNLOADED);
        refresher.refresh().join();
        results.clear();
        written.clear();

        refresher.refresh().join();

        assertEquals(List.of(FIRST), written);
    }

    @Test
    void unknownBoardsAreLeftAlone() {
        index.put(FIRST, "unknown");

        refresher.refresh().join();

        assertTrue(counted.isEmpty());
        assertTrue(written.isEmpty());
        assertEquals(1, index.size());
    }

    @Test
    void failedCountLeavesTheSign() {
        var failing = new SignBoardRefresher(
                index,
                spec -> Optional.of(new BoardTarget(null, BoardFilter.ofMaterials(List.of(spec)), spec)),
                filter -> CompletableFuture.failedFuture(new IllegalStateException("database down")),
                (location, target, count) -> {
                    written.add(location);
                    return WriteResult.WRITTEN;
                },
                schedule,
                Runnable::run,
                Logger.getLogger("test"));
        index.put(FIRST, "stone");

        failing.refresh().join();

        assertTrue(written.isEmpty());
    }

    @Test
    void rerenderWritesEverySignAgain() {
        index.put(FIRST, "stone");
        refresher.refresh().join();
        written.clear();

        refresher.rerender();
        refresher.tick();

        assertEquals(List.of(FIRST), written);
    }

    @Test
    void tickFollowsTheSchedule() {
        index.put(FIRST, "stone");
        refresher.tick();
        assertEquals(1, counted.size());

        refresher.tick();
        assertEquals(1, counted.size());

        refresher.markDirty();
        clock.advance(Duration.ofSeconds(4));
        refresher.tick();
        assertEquals(1, counted.size());

        clock.advance(Duration.ofSeconds(1));
        refresher.tick();
        assertEquals(2, counted.size());

        clock.advance(Duration.ofSeconds(59));
        refresher.tick();
        assertEquals(2, counted.size());

        clock.advance(Duration.ofSeconds(1));
        refresher.tick();
        assertEquals(3, counted.size());
    }

    @Test
    void scheduleIsDueFirstAndWhenForced() {
        assertTrue(schedule.due());
        schedule.started();
        assertFalse(schedule.due());

        schedule.force();
        assertTrue(schedule.due());
        schedule.started();

        schedule.markDirty();
        assertFalse(schedule.due());
        clock.advance(Duration.ofSeconds(5));
        assertTrue(schedule.due());
    }
}
