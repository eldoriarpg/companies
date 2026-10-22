/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

class SqLiteOrderDataTest {
    private static final int COMPANY = 1;
    private final OfflinePlayer owner = SqLiteTestDatabase.player(UUID.randomUUID());
    private final OfflinePlayer worker = SqLiteTestDatabase.player(UUID.randomUUID());
    @TempDir
    Path directory;
    private SqLiteOrderData orderData;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        orderData = new SqLiteOrderData(null, SqLiteTestDatabase.mapper());
    }

    private SimpleOrder createOrder(int stone, int dirt) {
        var contents = List.of(new OrderContent(new ItemStack(Material.STONE), stone, 100.0),
                new OrderContent(new ItemStack(Material.DIRT), dirt, 50.0));
        orderData.submitOrder(owner, new SimpleOrder(owner.getUniqueId(), "test").toFullOrder(contents)).join();
        var orders = orderData.retrieveOrdersByPlayer(owner, OrderState.UNCLAIMED, OrderState.RECEIVED).join();
        return orders.get(orders.size() - 1);
    }

    private SimpleOrder reload(SimpleOrder order) {
        return orderData.retrieveOrderById(order.id()).join().orElseThrow();
    }

    private void claim(SimpleOrder order) {
        SqLiteTestDatabase.setState(order.id(), OrderState.CLAIMED.stateId(), COMPANY);
    }

    private void addDelivery(SimpleOrder order, Material material, int amount) {
        query("INSERT INTO orders_delivered(id, worker_uuid, material, delivered) VALUES (?, ?, ?, ?)")
                .single(call().bind(order.id())
                        .bind(worker.getUniqueId(), UUID_BYTES)
                        .bind(material.name())
                        .bind(amount))
                .insert();
    }

    @Test
    void unclaimMovesDeliveriesToItemReturns() {
        var order = createOrder(10, 5);
        claim(order);
        addDelivery(order, Material.STONE, 4);

        var workers = orderData.submitUnclaimOrder(reload(order)).join();

        Assertions.assertEquals(Set.of(worker.getUniqueId()), workers.orElseThrow());
        Assertions.assertEquals(OrderState.UNCLAIMED, reload(order).state());
        Assertions.assertEquals(0, SqLiteTestDatabase.count("orders_delivered", "id = " + order.id()));
        var returns = orderData.retrieveItemReturns(worker.getUniqueId()).join();
        Assertions.assertEquals(1, returns.size());
        Assertions.assertEquals(Material.STONE, returns.get(0).stack().getType());
        Assertions.assertEquals(4, returns.get(0).amount());
    }

    @Test
    void unclaimIgnoresOrdersWhichAreNotClaimed() {
        var order = createOrder(10, 5);
        claim(order);
        addDelivery(order, Material.STONE, 4);
        SqLiteTestDatabase.setState(order.id(), OrderState.RECEIVED.stateId(), COMPANY);

        var workers = orderData.submitUnclaimOrder(reload(order)).join();

        Assertions.assertTrue(workers.isEmpty());
        Assertions.assertEquals(OrderState.RECEIVED, reload(order).state());
        Assertions.assertEquals(1, SqLiteTestDatabase.count("orders_delivered", "id = " + order.id()));
        Assertions.assertTrue(orderData.retrieveItemReturns(worker.getUniqueId()).join().isEmpty());
    }

    @Test
    void itemReturnsCanOnlyBeTakenOnce() {
        orderData.submitItemReturn(worker.getUniqueId(), new ItemStack(Material.STONE), 10).join();
        var itemReturn = orderData.retrieveItemReturns(worker.getUniqueId()).join().get(0);

        Assertions.assertTrue(orderData.submitItemReturnTake(itemReturn, 6).join());
        Assertions.assertFalse(orderData.submitItemReturnTake(itemReturn, 6).join());
        Assertions.assertEquals(4, orderData.retrieveItemReturns(worker.getUniqueId()).join().get(0).amount());
        Assertions.assertTrue(orderData.submitItemReturnTake(itemReturn, 4).join());
        Assertions.assertTrue(orderData.retrieveItemReturns(worker.getUniqueId()).join().isEmpty());
    }

    @Test
    void deliveryIsCappedToMissingAmount() {
        var order = createOrder(10, 5);
        claim(order);

        Assertions.assertEquals(6, orderData.submitDelivery(worker, reload(order), Material.STONE, 6).join());
        Assertions.assertEquals(4, orderData.submitDelivery(worker, reload(order), Material.STONE, 6).join());
        Assertions.assertEquals(0, orderData.submitDelivery(worker, reload(order), Material.STONE, 6).join());

        var content = orderData.retrieveFullOrder(reload(order)).join().content(Material.STONE).orElseThrow();
        Assertions.assertEquals(10, content.delivered());
        Assertions.assertEquals(1, SqLiteTestDatabase.count("orders_delivered", "id = " + order.id()));
    }

    @Test
    void deliveryRequiresClaimedOrder() {
        var order = createOrder(10, 5);

        Assertions.assertEquals(0, orderData.submitDelivery(worker, reload(order), Material.STONE, 5).join());

        SqLiteTestDatabase.setState(order.id(), OrderState.DELIVERED.stateId(), COMPANY);
        Assertions.assertEquals(0, orderData.submitDelivery(worker, reload(order), Material.STONE, 5).join());
        Assertions.assertEquals(0, SqLiteTestDatabase.count("orders_delivered", "id = " + order.id()));
    }

    @Test
    void orderIsMarkedDeliveredOnlyOnceAndWhenComplete() {
        var order = createOrder(10, 5);
        claim(order);
        orderData.submitDelivery(worker, reload(order), Material.STONE, 10).join();

        Assertions.assertTrue(orderData.submitOrderDelivered(reload(order)).join().isEmpty());

        orderData.submitDelivery(worker, reload(order), Material.DIRT, 5).join();
        var delivered = orderData.submitOrderDelivered(reload(order)).join();

        Assertions.assertTrue(delivered.isPresent());
        Assertions.assertEquals(OrderState.DELIVERED, delivered.get().state());
        Assertions.assertEquals(15, delivered.get().delivered());
        Assertions.assertTrue(orderData.submitOrderDelivered(reload(order)).join().isEmpty());
    }

    @Test
    void orderCanOnlyBeReceivedOnce() {
        var order = createOrder(10, 5);
        SqLiteTestDatabase.setState(order.id(), OrderState.DELIVERED.stateId(), COMPANY);

        Assertions.assertTrue(orderData.submitOrderStateUpdate(reload(order), OrderState.DELIVERED, OrderState.RECEIVED).join());
        Assertions.assertFalse(orderData.submitOrderStateUpdate(reload(order), OrderState.DELIVERED, OrderState.RECEIVED).join());
        Assertions.assertEquals(OrderState.RECEIVED, reload(order).state());
    }

    @Test
    void onlyUnclaimedOrdersCanBeDeleted() {
        var claimed = createOrder(10, 5);
        claim(claimed);
        var unclaimed = createOrder(10, 5);

        Assertions.assertFalse(orderData.submitOrderDeletion(reload(claimed)).join());
        Assertions.assertTrue(orderData.retrieveOrderById(claimed.id()).join().isPresent());
        Assertions.assertTrue(orderData.submitOrderDeletion(reload(unclaimed)).join());
        Assertions.assertFalse(orderData.submitOrderDeletion(unclaimed).join());
        Assertions.assertTrue(orderData.retrieveOrderById(unclaimed.id()).join().isEmpty());
    }
}
