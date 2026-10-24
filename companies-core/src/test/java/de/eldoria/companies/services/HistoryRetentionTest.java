/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.components.order.HistoryOutcome;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteHistoryData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.history.HistoryEntry;
import de.eldoria.companies.data.wrapper.history.HistorySnapshot;
import de.eldoria.companies.util.TestExecutors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HistoryRetentionTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 1, 12, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
    private static final HistorySnapshot EMPTY = new HistorySnapshot(List.of(), 10, 0, 0, 0, 0, List.of());

    private final UUID owner = UUID.randomUUID();
    private final AtomicInteger days = new AtomicInteger(90);

    @TempDir
    Path directory;

    private ExecutorService executor;
    private SqLiteHistoryData history;
    private HistoryRetention retention;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(2);
        history = new SqLiteHistoryData(SqLiteTestDatabase.mapper(), executor);
        retention = new HistoryRetention(history, days::get, CLOCK, Logger.getAnonymousLogger());
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
    }

    private void ownerEntry(int order, LocalDateTime finished) {
        history.submitEntry(HistoryEntry.ofOwner(
                        order, owner, HistoryOutcome.RECEIVED, "order", finished.minusDays(1), finished, EMPTY))
                .join();
    }

    private void companyEntry(int order, LocalDateTime finished) {
        history.submitEntry(HistoryEntry.ofCompany(
                        order, 1, HistoryOutcome.DELIVERED, "order", finished.minusDays(1), finished, EMPTY))
                .join();
    }

    private List<Integer> ownerOrders() {
        return history.retrieveOwnerHistory(owner, 1, 50).join().stream()
                .map(HistoryEntry::order)
                .toList();
    }

    @Test
    void entriesOlderThanTheRetentionAreDeleted() {
        ownerEntry(1, NOW.minusDays(91));
        ownerEntry(2, NOW.minusDays(89));
        companyEntry(1, NOW.minusDays(120));
        companyEntry(2, NOW.minusHours(1));

        retention.run();

        assertEquals(List.of(2), ownerOrders());
        assertEquals(
                List.of(2),
                history.retrieveCompanyHistory(1, 1, 50).join().stream()
                        .map(HistoryEntry::order)
                        .toList());
    }

    @Test
    void retentionFollowsTheConfiguredDays() {
        ownerEntry(1, NOW.minusDays(10));
        ownerEntry(2, NOW.minusDays(3));
        days.set(7);

        retention.run();

        assertEquals(NOW.minusDays(7), retention.cutoff());
        assertEquals(List.of(2), ownerOrders());
    }
}
