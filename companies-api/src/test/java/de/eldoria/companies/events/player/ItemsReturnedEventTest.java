/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.player;

import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.testing.Stubs;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemsReturnedEventTest {
    private static ServerMock server;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void exposesItemsReasonAndOrder() {
        var player = server.addPlayer();
        var order = Stubs.of(ISimpleOrder.class, Map.of("id", 4));
        var stacks = new ArrayList<>(List.of(new ItemsReturnedEvent.ReturnedStack(ItemStack.of(Material.STONE), 70)));

        var event = new ItemsReturnedEvent(player, stacks, ItemsReturnedEvent.Reason.ORDER_ABORTED, order);
        stacks.clear();

        assertSame(player, event.player());
        assertEquals(1, event.stacks().size());
        assertEquals(70, event.stacks().getFirst().amount());
        assertEquals(ItemsReturnedEvent.Reason.ORDER_ABORTED, event.reason());
        assertSame(order, event.order().orElseThrow());
    }

    @Test
    void grantsHaveNoOrder() {
        var event = new ItemsReturnedEvent(server.addPlayer(), List.of(), ItemsReturnedEvent.Reason.API, null);

        assertTrue(event.order().isEmpty());
    }

    @Test
    void returnedStackIsACopyWithAmountOne() {
        var item = ItemStack.of(Material.DIAMOND, 5);

        var stack = new ItemsReturnedEvent.ReturnedStack(item, 5);
        stack.stack().setAmount(3);

        assertEquals(1, stack.stack().getAmount());
        assertEquals(5, item.getAmount());
        assertThrows(IllegalArgumentException.class, () -> new ItemsReturnedEvent.ReturnedStack(item, 0));
    }
}
