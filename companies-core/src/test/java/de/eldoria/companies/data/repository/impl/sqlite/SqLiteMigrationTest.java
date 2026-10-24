/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.chojo.sadu.core.updater.SqlVersion;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.order.DeliveredStack;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * Upgrades a database of the previous version with orders, deliveries and returns, and checks the converted lines and
 * delivered stacks.
 */
class SqLiteMigrationTest {
    private final OfflinePlayer owner = SqLiteTestDatabase.player(UUID.randomUUID());
    private final OfflinePlayer worker = SqLiteTestDatabase.player(UUID.randomUUID());
    private final OfflinePlayer other = SqLiteTestDatabase.player(UUID.randomUUID());

    @TempDir
    Path directory;

    private SqLiteOrderData orderData;

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
        SqLiteTestDatabase.create(directory, new SqlVersion(1, 3));
        var claimed = insertOrder(OrderState.CLAIMED);
        insertContent(claimed, Material.STONE, 10, 100.0);
        insertContent(claimed, Material.DIRT, 5, 50.0);
        insertContent(claimed, Material.DIRT, 3, 30.0);
        insertDelivery(claimed, worker, Material.STONE, 4);
        insertDelivery(claimed, worker, Material.DIRT, 2);
        insertDelivery(claimed, other, Material.STONE, 1);
        insertDelivery(claimed, worker, Material.SAND, 9);
        var open = insertOrder(OrderState.UNCLAIMED);
        insertContent(open, Material.GRAVEL, 7, 14.0);
        query("INSERT INTO item_returns(worker_uuid, kind, material, stack, amount) VALUES (?, 0, 'STONE', ?, 2)")
                .single(call().bind(worker.getUniqueId(), UUID_BYTES).bind(json(Material.STONE)))
                .insert();

        SqLiteTestDatabase.create(directory);
        orderData = new SqLiteOrderData(null, SqLiteTestDatabase.mapper());
    }

    private int insertOrder(OrderState state) {
        var id = query("INSERT INTO orders(owner_uuid, name) VALUES (?, 'legacy') RETURNING id")
                .single(call().bind(owner.getUniqueId(), UUID_BYTES))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
        query("INSERT INTO order_states(id, state, company) VALUES (?, ?, 1)")
                .single(call().bind(id).bind(state.stateId()))
                .insert();
        return id;
    }

    private static void insertContent(int order, Material material, int amount, double price) {
        query("INSERT INTO order_content(id, material, stack, amount, price) VALUES (?, ?, ?, ?, ?)")
                .single(call().bind(order)
                        .bind(material.name())
                        .bind(json(material))
                        .bind(amount)
                        .bind(price))
                .insert();
    }

    private static void insertDelivery(int order, OfflinePlayer worker, Material material, int amount) {
        query("INSERT INTO orders_delivered(id, worker_uuid, material, delivered) VALUES (?, ?, ?, ?)")
                .single(call().bind(order)
                        .bind(worker.getUniqueId(), UUID_BYTES)
                        .bind(material.name())
                        .bind(amount))
                .insert();
    }

    private static String json(Material material) {
        return "{\"type\":\"" + material.name() + "\",\"amount\":1}";
    }

    private FullOrder order(int id) {
        return orderData
                .retrieveFullOrder(orderData.retrieveOrderById(id).join().orElseThrow())
                .join();
    }

    private static Map<UUID, Integer> byWorker(OrderContent content) {
        return content.deliveries().stream()
                .collect(Collectors.toMap(DeliveredStack::worker, DeliveredStack::amount, Integer::sum));
    }

    @Test
    void linesAreNumberedPerOrderByMaterial() {
        var claimed = order(1).contents();
        var open = order(2).contents();

        Assertions.assertEquals(
                List.of(Material.DIRT, Material.STONE),
                claimed.stream().map(OrderContent::material).toList());
        Assertions.assertEquals(
                List.of(0, 1), claimed.stream().map(OrderContent::line).toList());
        Assertions.assertEquals(
                List.of(0), open.stream().map(OrderContent::line).toList());
        Assertions.assertTrue(claimed.stream().noneMatch(OrderContent::exact));
    }

    @Test
    void duplicateLinesAreMergedAndPricesKept() {
        var claimed = order(1);

        var dirt = claimed.line(0).orElseThrow();
        Assertions.assertEquals(8, dirt.amount());
        Assertions.assertEquals(80.0, dirt.price(), 0.0001);
        Assertions.assertEquals(180.0, claimed.price(), 0.0001);
        Assertions.assertEquals(14.0, order(2).price(), 0.0001);
    }

    @Test
    void deliveriesBecomeDeliveredStacksOfTheirLine() {
        var claimed = order(1);

        var stone = claimed.line(1).orElseThrow();
        Assertions.assertEquals(Map.of(worker.getUniqueId(), 4, other.getUniqueId(), 1), byWorker(stone));
        Assertions.assertTrue(stone.deliveries().stream()
                .allMatch(delivery -> delivery.stack().equals(new ItemStack(Material.STONE))));
        var dirt = claimed.line(0).orElseThrow();
        Assertions.assertEquals(Map.of(worker.getUniqueId(), 2), byWorker(dirt));
        Assertions.assertEquals(7, claimed.delivered());
    }

    @Test
    void orphanedDeliveriesAreDroppedWithTheOldTable() {
        Assertions.assertEquals(3, SqLiteTestDatabase.count("order_deliveries", "TRUE"));
        Assertions.assertEquals(
                0, SqLiteTestDatabase.count("sqlite_master", "type = 'table' AND name = 'orders_delivered'"));
    }

    @Test
    void convertedDeliveriesAndReturnsAreHandedBack() {
        var released = orderData.submitUnclaimOrder(order(1)).join();

        Assertions.assertTrue(released.isPresent());
        Assertions.assertEquals(
                8,
                orderData
                        .retrieveItemReturnCount(worker.getUniqueId(), ReturnKind.WORKER)
                        .join());
        Assertions.assertEquals(
                1,
                orderData
                        .retrieveItemReturnCount(other.getUniqueId(), ReturnKind.WORKER)
                        .join());
    }
}
