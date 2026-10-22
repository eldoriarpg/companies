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
}
