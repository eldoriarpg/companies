/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.order.HistoryOutcome;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteHistoryData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.history.HistoryEntry;
import de.eldoria.companies.data.wrapper.history.HistorySnapshot;
import de.eldoria.companies.data.wrapper.order.ItemAmount;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.EventRecorder;
import de.eldoria.companies.events.order.OrderReceivedEvent;
import de.eldoria.companies.events.order.OrderRemovedEvent;
import de.eldoria.companies.events.order.SoloOrderReceivedEvent;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.util.TestExecutors;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
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
import java.util.stream.Collectors;

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
    private EventRecorder events;

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
        var plugin = MockBukkit.createMockPlugin();
        events = EventRecorder.listen(plugin, ItemsReturnedEvent.class);
        outcomes = new OrderOutcomes(
                orders.orderData(),
                recorder,
                new ItemReturnService(plugin, orders.orderData()),
                events,
                id -> ISimpleCompany.forId(id),
                Logger.getAnonymousLogger(),
                executor);
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
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

        var returned = outcomes.abort(orders.reload(order)).join();

        assertEquals(Set.of(worker), returned.orElseThrow().workers());
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

    @Test
    void receivingFiresTheReceptionAndTheOverflowOnce() {
        var order = orders.place(5.0);
        orders.state(order, OrderState.CLAIMED, COMPANY);
        orders.deliver(order, 0, Material.STONE, worker, 10);
        orders.deliver(order, 1, Material.DIRT, worker, 5);
        orders.state(order, OrderState.DELIVERED, COMPANY);
        var full = orders.full(order);
        var pending = List.of(new ItemAmount(new ItemStack(Material.DIRT), 3));

        assertTrue(outcomes.receive(full, pending).join());
        assertFalse(outcomes.receive(full, pending).join());

        var received = events.single(OrderReceivedEvent.class);
        assertEquals(order.id(), received.order().id());
        assertEquals(OrderState.RECEIVED, received.order().state());
        assertEquals(COMPANY, received.company().id());
        assertTrue(received.isAsynchronous());
        var overflow = events.single(ItemsReturnedEvent.class);
        assertEquals(ItemsReturnedEvent.Reason.DELIVERY_OVERFLOW, overflow.reason());
        assertEquals(orders.owner().getUniqueId(), overflow.player().getUniqueId());
        assertEquals(3, overflow.stacks().get(0).amount());
        assertEquals(order.id(), overflow.order().orElseThrow().id());
    }

    @Test
    void receivingWithoutOverflowReturnsNothing() {
        var order = orders.place(0.0);
        orders.state(order, OrderState.DELIVERED, COMPANY);

        assertTrue(outcomes.receive(orders.full(order), List.of()).join());

        assertEquals(1, events.of(OrderReceivedEvent.class).size());
        assertTrue(events.of(ItemsReturnedEvent.class).isEmpty());
    }

    @Test
    void cancellingFiresTheRemovalAsCancelled() {
        var order = orders.place(0.0);
        var full = orders.full(order);

        assertTrue(outcomes.cancel(full).join());
        assertFalse(outcomes.cancel(full).join());

        var removed = events.single(OrderRemovedEvent.class);
        assertEquals(OrderRemovedEvent.Reason.CANCELLED, removed.reason());
        assertEquals(order.id(), removed.order().id());
    }

    @Test
    void purgingFiresTheRemovalAsPurged() {
        var order = orders.place(0.0);

        assertTrue(outcomes.purge(orders.full(order)).join());

        assertEquals(
                OrderRemovedEvent.Reason.PURGED,
                events.single(OrderRemovedEvent.class).reason());
    }

    @Test
    void cancellingAClaimedOrderFiresNothing() {
        var order = orders.place(0.0);
        var full = orders.full(order);
        orders.state(order, OrderState.CLAIMED, COMPANY);

        assertFalse(outcomes.cancel(full).join());

        assertTrue(events.of(OrderRemovedEvent.class).isEmpty());
    }

    @Test
    void abortingReturnsTheDeliveriesOfEveryWorker() {
        var other = UUID.randomUUID();
        var order = orders.place(0.0);
        orders.state(order, OrderState.CLAIMED, COMPANY);
        orders.deliver(order, 0, Material.STONE, worker, 4);
        orders.deliver(order, 1, Material.DIRT, other, 2);

        outcomes.abort(orders.reload(order)).join();

        var returned = events.of(ItemsReturnedEvent.class);
        assertEquals(2, returned.size());
        assertTrue(returned.stream().allMatch(event -> event.reason() == ItemsReturnedEvent.Reason.ORDER_ABORTED));
        assertTrue(returned.stream().allMatch(ItemsReturnedEvent::isAsynchronous));
        assertEquals(
                Set.of(worker, other),
                returned.stream().map(event -> event.player().getUniqueId()).collect(Collectors.toSet()));
    }

    @Test
    void expiryReturnsTheDeliveriesAsExpired() {
        var order = orders.place(0.0);
        orders.state(order, OrderState.CLAIMED, COMPANY);
        orders.deliver(order, 0, Material.STONE, worker, 4);

        outcomes.expire(orders.reload(order)).join();
        outcomes.expire(orders.reload(order)).join();

        var returned = events.single(ItemsReturnedEvent.class);
        assertEquals(ItemsReturnedEvent.Reason.ORDER_EXPIRED, returned.reason());
        assertEquals(4, returned.stacks().get(0).amount());
        assertEquals(Material.STONE, returned.stacks().get(0).stack().getType());
        assertEquals(order.id(), returned.order().orElseThrow().id());
    }

    @Test
    void releasingWithoutDeliveriesReturnsNothing() {
        var order = orders.place(0.0);
        orders.state(order, OrderState.CLAIMED, COMPANY);

        assertTrue(outcomes.abort(orders.reload(order)).join().isPresent());

        assertTrue(events.of(ItemsReturnedEvent.class).isEmpty());
    }

    private SimpleOrder soloClaimed() {
        var order = orders.place(0.0);
        assertTrue(orders.orderData().submitSoloClaim(worker, order, 1).join());
        orders.deliver(order, 0, Material.STONE, worker, 4);
        return orders.reload(order);
    }

    @Test
    void abortingASoloOrderReturnsTheItemsWithoutHistory() {
        var order = soloClaimed();

        var returned = outcomes.abort(order).join().orElseThrow();

        assertEquals(Set.of(worker), returned.workers());
        assertEquals(
                ItemsReturnedEvent.Reason.ORDER_ABORTED,
                events.single(ItemsReturnedEvent.class).reason());
        assertEquals(OrderState.UNCLAIMED, orders.reload(order).state());
        assertEquals(0, SqLiteTestDatabase.count("order_history", "1 = 1"));
    }

    @Test
    void expiringASoloOrderReturnsTheItemsWithoutHistory() {
        var order = soloClaimed();

        assertTrue(outcomes.expire(order).join().isPresent());
        assertTrue(outcomes.expire(orders.reload(order)).join().isEmpty());

        var returned = events.single(ItemsReturnedEvent.class);
        assertEquals(ItemsReturnedEvent.Reason.ORDER_EXPIRED, returned.reason());
        assertEquals(worker, returned.player().getUniqueId());
        assertEquals(0, SqLiteTestDatabase.count("order_history", "1 = 1"));
    }

    @Test
    void receivingASoloOrderFiresTheSoloReception() {
        var order = soloClaimed();
        orders.deliver(order, 0, Material.STONE, worker, 6);
        orders.deliver(order, 1, Material.DIRT, worker, 5);
        SqLiteTestDatabase.setState(order.id(), OrderState.DELIVERED.stateId(), 0);

        assertTrue(outcomes.receive(orders.full(order), List.of()).join());

        var received = events.single(SoloOrderReceivedEvent.class);
        assertEquals(worker, received.player().getUniqueId());
        assertEquals(OrderState.RECEIVED, received.order().state());
        assertTrue(events.of(OrderReceivedEvent.class).isEmpty());
        assertOwnerOnly(HistoryOutcome.RECEIVED);
    }
}
