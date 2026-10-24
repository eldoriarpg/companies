/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

import de.eldoria.companies.components.order.IFullOrder;
import de.eldoria.companies.testing.Stubs;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderRequestTest {
    private static final UUID OWNER = UUID.randomUUID();

    @BeforeAll
    static void setUp() {
        MockBukkit.mock();
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void builderChargesAndRespectsLimitsByDefault() {
        var request = OrderRequest.builder(OWNER, "order")
                .line(OrderLineRequest.material(Material.STONE, 10, 1.5))
                .build();

        assertTrue(request.charge());
        assertTrue(request.respectLimits());
        assertEquals(OWNER, request.owner());
        assertEquals("order", request.name());
        assertEquals(15.0, request.price());
    }

    @Test
    void builderTakesTheCallersChoice() {
        var request = OrderRequest.builder(OWNER, "reward")
                .line(OrderLineRequest.material(Material.STONE, 1, 1.0))
                .charge(false)
                .respectLimits(false)
                .build();

        assertFalse(request.charge());
        assertFalse(request.respectLimits());
    }

    @Test
    void rejectsRequestsWithoutLinesOrName() {
        var builder = OrderRequest.builder(OWNER, "order");
        var line = OrderLineRequest.material(Material.STONE, 1, 1.0);

        assertThrows(IllegalArgumentException.class, builder::build);
        assertThrows(
                IllegalArgumentException.class,
                () -> OrderRequest.builder(OWNER, " ").line(line).build());
        assertThrows(NullPointerException.class, () -> OrderRequest.builder(null, "order"));
    }

    @Test
    void linesAreCopied() {
        var lines = new ArrayList<OrderLineRequest>();
        lines.add(OrderLineRequest.material(Material.STONE, 1, 1.0));
        var request = new OrderRequest(OWNER, "order", lines, true, true);

        lines.clear();

        assertEquals(1, request.lines().size());
        assertThrows(UnsupportedOperationException.class, () -> request.lines().clear());
    }

    @Test
    void exactLineCopiesTemplateWithAmountOne() {
        var template = ItemStack.of(Material.DIAMOND_SWORD, 5);

        var line = OrderLineRequest.exact(template, 2, 10.0);
        line.template().setAmount(20);

        assertTrue(line.exact());
        assertEquals(1, line.template().getAmount());
        assertEquals(5, template.getAmount());
        assertEquals(20.0, line.price());
    }

    @Test
    void rejectsInvalidLines() {
        assertThrows(IllegalArgumentException.class, () -> OrderLineRequest.material(Material.AIR, 1, 1.0));
        assertThrows(IllegalArgumentException.class, () -> OrderLineRequest.material(Material.WATER, 1, 1.0));
        assertThrows(IllegalArgumentException.class, () -> OrderLineRequest.material(Material.STONE, 0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> OrderLineRequest.material(Material.STONE, 1, -0.5));
        assertThrows(
                IllegalArgumentException.class,
                () -> OrderLineRequest.exact(ItemStack.of(Material.STONE), 1, Double.POSITIVE_INFINITY));
    }

    @Test
    void resultsTellWhetherTheOrderWasCreated() {
        CreateOrderResult created = new CreateOrderResult.Created(Stubs.of(IFullOrder.class, Map.of()));
        CreateOrderResult rejected = new CreateOrderResult.Rejected(RejectReason.CANCELLED, Component.text("no"));

        assertTrue(created.created());
        assertFalse(rejected.created());
        assertThrows(NullPointerException.class, () -> new CreateOrderResult.Rejected(null, Component.empty()));
    }
}
