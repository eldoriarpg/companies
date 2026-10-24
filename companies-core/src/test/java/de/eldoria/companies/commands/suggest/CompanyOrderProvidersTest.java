/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.order.DeliveredStack;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

class CompanyOrderProvidersTest {
    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    private static FullOrder order(int id, OrderContent... contents) {
        return new FullOrder(
                id,
                UUID.randomUUID(),
                "order " + id,
                LocalDateTime.now(),
                1,
                LocalDateTime.now(),
                OrderState.CLAIMED,
                0.0,
                0,
                0,
                List.of(contents));
    }

    private static OrderContent line(int line, int amount, int delivered) {
        var content = new OrderContent(line, new ItemStack(Material.STONE), false, amount, 10.0);
        if (delivered > 0) {
            content.deliveries(
                    List.of(new DeliveredStack(1, UUID.randomUUID(), new ItemStack(Material.STONE), delivered)));
        }
        return content;
    }

    @Test
    void ordersAreMatchedByTheStartOfTheirId() {
        var orders = List.of(order(1), order(12), order(21));

        Assertions.assertEquals(
                List.of(1, 12),
                CompanyOrderProviders.matchingOrders(orders, "1").stream()
                        .map(FullOrder::id)
                        .toList());
        Assertions.assertEquals(
                3, CompanyOrderProviders.matchingOrders(orders, "").size());
        Assertions.assertTrue(CompanyOrderProviders.matchingOrders(orders, "x").isEmpty());
    }

    @Test
    void onlyLinesThatMissItemsAreSuggestedFromOne() {
        var order = order(1, line(0, 10, 10), line(1, 10, 3), line(2, 5, 0));

        Assertions.assertEquals(List.of(2, 3), CompanyOrderProviders.missingLines(order, ""));
        Assertions.assertEquals(List.of(3), CompanyOrderProviders.missingLines(order, "3"));
    }

    @Test
    void ordersAreFoundById() {
        var orders = List.of(order(4), order(7));

        Assertions.assertEquals(
                7, CompanyOrderProviders.order(orders, 7).orElseThrow().id());
        Assertions.assertTrue(CompanyOrderProviders.order(orders, 5).isEmpty());
    }

    @Test
    void sortingsAreLowerCase() {
        Assertions.assertTrue(CompanyOrderProviders.sortingNames().contains("unit_price"));
    }
}
