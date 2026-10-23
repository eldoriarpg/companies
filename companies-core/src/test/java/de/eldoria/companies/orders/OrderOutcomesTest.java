/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.order.HistoryOutcome;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteHistoryData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.history.HistoryEntry;
import de.eldoria.companies.data.wrapper.history.HistorySnapshot;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderOutcomesTest {
    private static final int COMPANY = 3;

    private final UUID worker = UUID.randomUUID();

    @TempDir
    Path directory;

    private ExecutorService executor;
    private OrderTable orders;
    private SqLiteHistoryData history;
    private OrderOutcomes outcomes;

    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(4);
        orders = OrderTable.create();
        history = new SqLiteHistoryData(SqLiteTestDatabase.mapper(), executor);
        var recorder = new HistoryRecorder(history, Clock.systemDefaultZone());
        outcomes = new OrderOutcomes(orders.orderData(), recorder, Logger.getAnonymousLogger(), executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private List<HistoryEntry> ownerEntries() {
        return history.retrieveOwnerHistory(orders.owner().getUniqueId(), 1, 50).join();
    }

    private List<HistoryEntry> companyEntries() {
        return history.retrieveCompanyHistory(COMPANY, 1, 50).join();
    }

    private void assertOwnerOnly(HistoryOutcome outcome) {
        var entries = ownerEntries();
        assertEquals(1, entries.size());
        var entry = entries.get(0);
        assertEquals(outcome, entry.outcome());
        assertNull(entry.company());
        assertTrue(entry.snapshot().workers().isEmpty());
        assertTrue(companyEntries().isEmpty());
    }

    @Test
    void receivingRecordsTheOwnerOnce() {
        var order = orders.place(5.0);
        orders.state(order, OrderState.CLAIMED, COMPANY);
        orders.deliver(order, 0, Material.STONE, worker, 10);
        orders.deliver(order, 1, Material.DIRT, worker, 5);
        orders.state(order, OrderState.DELIVERED, COMPANY);
        var full = orders.full(order);

        assertTrue(outcomes.receive(full, List.of()).join());
        assertFalse(outcomes.receive(full, List.of()).join());

        assertOwnerOnly(HistoryOutcome.RECEIVED);
        var snapshot = ownerEntries().get(0).snapshot();
        assertEquals(150.0, snapshot.price());
        assertEquals(5.0, snapshot.fee());
        assertEquals(
                List.of(10, 5),
                snapshot.lines().stream().map(HistorySnapshot.Line::delivered).toList());
    }

    @Test
    void cancellingRecordsTheOwner() {
        var order = orders.place(0.0);

        assertTrue(outcomes.cancel(orders.full(order)).join());

        assertOwnerOnly(HistoryOutcome.CANCELLED);
    }

    @Test
    void cancellingAClaimedOrderRecordsNothing() {
        var order = orders.place(0.0);
        var full = orders.full(order);
        orders.state(order, OrderState.CLAIMED, COMPANY);

        assertFalse(outcomes.cancel(full).join());

        assertTrue(ownerEntries().isEmpty());
    }

    @Test
    void purgingRecordsTheOwner() {
        var order = orders.place(2.0);

        assertTrue(outcomes.purge(orders.full(order)).join());

        assertOwnerOnly(HistoryOutcome.PURGED);
    }

    @Test
    void abortingRecordsTheCompanyWithTheReturnedDeliveries() {
        var order = orders.place(0.0);
        orders.state(order, OrderState.CLAIMED, COMPANY);
        orders.deliver(order, 0, Material.STONE, worker, 4);

        var workers = outcomes.abort(orders.reload(order)).join();

        assertEquals(Set.of(worker), workers.orElseThrow());
        assertTrue(ownerEntries().isEmpty());
        var entries = companyEntries();
        assertEquals(1, entries.size());
        var entry = entries.get(0);
        assertEquals(HistoryOutcome.ABORTED, entry.outcome());
        assertNull(entry.owner());
        assertEquals(4, entry.snapshot().lines().get(0).delivered());
        assertEquals(
                List.of(new HistorySnapshot.Worker(worker, 4, 0, 0)),
                entry.snapshot().workers());
    }

    @Test
    void expiryRecordsTheCompany() {
        var order = orders.place(0.0);
        orders.state(order, OrderState.CLAIMED, COMPANY);

        assertTrue(outcomes.expire(orders.reload(order)).join().isPresent());

        var entries = companyEntries();
        assertEquals(1, entries.size());
        assertEquals(HistoryOutcome.EXPIRED, entries.get(0).outcome());
    }

    @Test
    void releasingAnUnclaimedOrderRecordsNothing() {
        var order = orders.place(0.0);

        assertTrue(outcomes.abort(order).join().isEmpty());
        assertTrue(outcomes.expire(order).join().isEmpty());

        assertTrue(companyEntries().isEmpty());
    }
}
