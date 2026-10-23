/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * Orders in a SQLite test database: one stone line of 10 for 100 and one dirt line of 5 for 50.
 *
 * @param orderData order repository of the database
 * @param owner     owner of the orders
 */
public record OrderTable(SqLiteOrderData orderData, OfflinePlayer owner) {
    public static OrderTable create() {
        return new OrderTable(
                new SqLiteOrderData(null, SqLiteTestDatabase.mapper()), SqLiteTestDatabase.player(UUID.randomUUID()));
    }

    public SimpleOrder place(double fee) {
        var contents = List.of(
                new OrderContent(0, new ItemStack(Material.STONE), false, 10, 100.0),
                new OrderContent(1, new ItemStack(Material.DIRT), false, 5, 50.0));
        orderData
                .submitOrder(owner, new SimpleOrder(owner.getUniqueId(), "test", fee).toFullOrder(contents))
                .join();
        var orders = orderData
                .retrieveOrdersByPlayer(owner, OrderState.UNCLAIMED, OrderState.RECEIVED)
                .join();
        return orders.get(orders.size() - 1);
    }

    public SimpleOrder reload(SimpleOrder order) {
        return orderData.retrieveOrderById(order.id()).join().orElseThrow();
    }

    public FullOrder full(SimpleOrder order) {
        return orderData.toFullOrder(reload(order));
    }

    public void state(SimpleOrder order, OrderState state, int company) {
        SqLiteTestDatabase.setState(order.id(), state.stateId(), company);
    }

    public void deliver(SimpleOrder order, int line, Material material, UUID worker, int amount) {
        query("INSERT INTO order_deliveries(order_id, line, worker_uuid, stack, amount) VALUES (?, ?, ?, ?, ?)")
                .single(call().bind(order.id())
                        .bind(line)
                        .bind(worker, UUID_BYTES)
                        .bind("{\"type\":\"" + material.name() + "\",\"amount\":1}")
                        .bind(amount))
                .insert();
    }
}
