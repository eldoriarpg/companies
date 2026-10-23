/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.ItemMatchSettings;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteHistoryData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteStatsData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.EventRecorder;
import de.eldoria.companies.events.order.OrderDeliveryEvent;
import de.eldoria.companies.events.order.OrderDoneEvent;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.util.TestExecutors;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryEventsTest {
    private static final int COMPANY = 4;
    private static final CompanyProfile PROFILE =
            new CompanyProfile(COMPANY, "Acme", LocalDateTime.now(), 1, CompanyDetails.NONE, List.of());

    @TempDir
    Path directory;

    private ServerMock server;
    private ExecutorService executor;
    private OrderTable orders;
    private DeliveryService service;
    private EventRecorder events;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(4);
        orders = OrderTable.create();
        var treasury = new OrderCompletionService.Treasury() {
            @Override
            public void pay(OfflinePlayer worker, double amount) {}

            @Override
            public void collect(double amount) {}
        };
        var completion = new OrderCompletionService(
                plugin,
                orders.orderData(),
                new SqLiteStatsData(executor),
                new HistoryRecorder(
                        new SqLiteHistoryData(SqLiteTestDatabase.mapper(), executor), Clock.systemDefaultZone()),
                treasury,
                level -> 0.0,
                () -> 5.0,
                Clock.systemDefaultZone(),
                executor);
        service = new DeliveryService(
                plugin,
                orders.orderData(),
                completion,
                new ItemReturnService(plugin, orders.orderData()),
                ItemMatchSettings::new);
        events = EventRecorder.listen(plugin, OrderDeliveryEvent.class, OrderDoneEvent.class, ItemsReturnedEvent.class);
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
        MockBukkit.unmock();
    }

    private <T> T await(CompletableFuture<T> future) throws InterruptedException {
        var deadline = System.currentTimeMillis() + 10_000;
        while (!future.isDone()) {
            if (System.currentTimeMillis() > deadline) throw new AssertionError("The delivery did not finish");
            server.getScheduler().performOneTick();
            Thread.sleep(5);
        }
        return future.join();
    }

    private static DeliveryPart part(int line, Material material, int amount) {
        return new DeliveryPart(line, new ComponentStack(material), amount);
    }

    /**
     * Claims the order and gives its lines templates the mock server can match.
     */
    private FullOrder claimed(SimpleOrder order) {
        orders.state(order, OrderState.CLAIMED, COMPANY);
        return orders.reload(order)
                .toFullOrder(List.of(
                        new OrderContent(0, new ComponentStack(Material.STONE), false, 10, 100.0),
                        new OrderContent(1, new ComponentStack(Material.DIRT), false, 5, 50.0)));
    }

    @Test
    void deliveryFiresOnceWithTheRecordedStacks() throws Exception {
        var worker = server.addPlayer();
        var order = orders.place(0.0);

        await(service.submit(worker, PROFILE, claimed(order), List.of(part(0, Material.STONE, 4))));

        var delivery = events.single(OrderDeliveryEvent.class);
        assertSame(worker, delivery.player());
        assertSame(PROFILE, delivery.company());
        assertEquals(order.id(), delivery.order().id());
        assertEquals(4, assertInstanceOf(FullOrder.class, delivery.order()).delivered());
        assertTrue(delivery.isAsynchronous());
        assertEquals(1, delivery.deliveries().size());
        var stack = delivery.deliveries().get(0);
        assertEquals(worker.getUniqueId(), stack.worker());
        assertEquals(Material.STONE, stack.stack().getType());
        assertEquals(4, stack.amount());
        assertTrue(events.of(OrderDoneEvent.class).isEmpty());
        assertTrue(events.of(ItemsReturnedEvent.class).isEmpty());
    }

    @Test
    void completingDeliveryFiresTheDeliveryBeforeTheCompletion() throws Exception {
        var worker = server.addPlayer();
        var order = orders.place(0.0);

        await(service.submit(
                worker, PROFILE, claimed(order), List.of(part(0, Material.STONE, 10), part(1, Material.DIRT, 5))));

        var delivery = events.single(OrderDeliveryEvent.class);
        assertEquals(2, delivery.deliveries().size());
        assertTrue(assertInstanceOf(FullOrder.class, delivery.order()).isDone());
        var deadline = System.currentTimeMillis() + 10_000;
        while (SqLiteTestDatabase.count("order_payouts", "order_id = " + order.id()) == 0
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertEquals(1, events.of(OrderDoneEvent.class).size());
    }

    @Test
    void surplusOfAnOfflineWorkerIsReturned() throws Exception {
        var worker = server.addPlayer();
        var order = orders.place(0.0);
        var full = claimed(order);
        worker.disconnect();

        await(service.submit(worker, PROFILE, full, List.of(part(0, Material.STONE, 13))));

        assertEquals(
                10, events.single(OrderDeliveryEvent.class).deliveries().get(0).amount());
        var deadline = System.currentTimeMillis() + 10_000;
        while (events.of(ItemsReturnedEvent.class).isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        var returned = events.single(ItemsReturnedEvent.class);
        assertEquals(ItemsReturnedEvent.Reason.DELIVERY_OVERFLOW, returned.reason());
        assertEquals(worker.getUniqueId(), returned.player().getUniqueId());
        assertEquals(3, returned.stacks().get(0).amount());
        assertEquals(order.id(), returned.order().orElseThrow().id());
    }

    @Test
    void deliveryWithoutItemsFiresNothing() throws Exception {
        var worker = server.addPlayer();
        var order = orders.place(0.0);

        await(service.submit(worker, PROFILE, claimed(order), List.of()));

        assertTrue(events.of(OrderDeliveryEvent.class).isEmpty());
    }

    @Test
    void unrecordedPartsAreNoDeliveries() {
        var worker = server.addPlayer();
        var parts = List.of(
                new DeliveryPart(0, new ItemStack(Material.STONE, 3), 10),
                new DeliveryPart(1, new ItemStack(Material.DIRT), 4));

        var delivered = DeliveryService.delivered(worker, parts, List.of(7, 0));

        assertEquals(1, delivered.size());
        assertEquals(7, delivered.get(0).amount());
        assertEquals(1, delivered.get(0).stack().getAmount());
    }
}
