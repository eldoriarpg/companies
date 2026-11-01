/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.order.ItemCriteria;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.ItemMatchSettings;
import de.eldoria.companies.data.wrapper.order.DeliveredStack;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.menus.order.OrderLineView;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The order views count damaged deliveries on the lines that take them: every line where the server allows damaged
 * items, otherwise only criteria lines with a durability condition.
 */
class OrderLineDamageTest {
    private static final UUID WORKER = UUID.randomUUID();

    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    @Test
    void aDurabilityLineShowsItsDamageWhereDamagedItemsAreRefused() {
        var kept = ItemCriteria.of("DIAMOND_SWORD").withMinDurability(50);
        var durability = new OrderContent(0, kept.template(), false, kept, 2, 10);
        durability.deliveries(List.of(new DeliveredStack(1, WORKER, worn(40), 1)));
        var plain = new OrderContent(1, ItemCriteria.of("DIAMOND_SWORD").template(), false, null, 2, 10);
        plain.deliveries(List.of(new DeliveredStack(2, WORKER, worn(40), 1)));
        var order = new FullOrder(
                7,
                UUID.randomUUID(),
                "Swords",
                LocalDateTime.now(),
                1,
                LocalDateTime.now(),
                OrderState.CLAIMED,
                0,
                1,
                0,
                List.of(durability, plain));

        var refusing = OrderLineView.withDamage(order, matcher(false));
        var allowing = OrderLineView.withDamage(order, matcher(true));

        assertEquals(1, refusing.get(0).damaged());
        assertEquals(0.4, refusing.get(0).worstDamage(), 1e-9);
        assertEquals(0, refusing.get(1).damaged());
        assertEquals(1, allowing.get(1).damaged());
    }

    private static ItemMatcher matcher(boolean allowDamaged) {
        return ItemMatcher.of(new ItemMatchSettings(List.of(), allowDamaged), Logger.getAnonymousLogger());
    }

    private static ComponentStack worn(int damage) {
        var stack = new ComponentStack(Material.DIAMOND_SWORD);
        stack.setData(DataComponentTypes.MAX_DAMAGE, 100);
        stack.setData(DataComponentTypes.DAMAGE, damage);
        return stack;
    }
}
