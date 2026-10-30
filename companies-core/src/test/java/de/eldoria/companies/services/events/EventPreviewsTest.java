/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import de.eldoria.companies.components.event.EventKind;
import de.eldoria.companies.components.order.ItemCriteria;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.UUID;

import static de.eldoria.companies.services.events.TestEvents.START;
import static de.eldoria.companies.services.events.TestEvents.event;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventPreviewsTest {
    private static final double DELTA = 0.0001;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theMarkShowsTheHighestFactorOfALineTheTaxAndTheFeeTakenOff() {
        var order = order();
        var stone = event(1, EventKind.REWARD_MULTIPLIER, 2, START, List.of("STONE"), List.of());
        var all = event(2, EventKind.REWARD_MULTIPLIER, 1.5, START, List.of(), List.of());
        var tax = event(3, EventKind.TAX_REDUCTION, 4, START.plusHours(1), List.of(), List.of());
        var fee = event(4, EventKind.FEE_REDUCTION, 30, START, List.of(), List.of());

        var mark = EventPreviews.mark(order, List.of(stone, all, tax, fee), 2.5).orElseThrow();

        assertEquals(2.5, mark.multiplier(), DELTA);
        assertEquals(4, mark.taxPoints(), DELTA);
        assertEquals(30, mark.feePercent(), DELTA);
        assertEquals(START.plusDays(1), mark.ends());
        assertTrue(EventPreviews.mark(order, List.of(), 3).isEmpty());
    }

    @Test
    void thePreviewPaysTheOrderWithTheEventsTaxAndExtra() {
        var order = order();
        var tax = event(1, EventKind.TAX_REDUCTION, 10, START, List.of(), List.of());
        var dirt = event(2, EventKind.REWARD_MULTIPLIER, 2, START.minusHours(1), List.of("DIRT"), List.of());

        var preview = EventPreviews.preview(order, List.of(tax, dirt), 10, 3).orElseThrow();

        assertEquals(150.0 + 50.0, preview.net(), DELTA);
        assertEquals(START.minusHours(1).plusDays(1), preview.ends());
        assertTrue(EventPreviews.preview(order, List.of(event(3, EventKind.FEE_REDUCTION, 50)), 10, 3)
                .isEmpty());
    }

    @Test
    void aCriteriaLinePaysOnAMaterialItAccepts() {
        var logs = new OrderContent(
                0, new ItemStack(Material.BIRCH_LOG), false, ItemCriteria.of("BIRCH_LOG", "SPRUCE_LOG"), 10, 100.0);
        var order = new SimpleOrder(UUID.randomUUID(), "logs", 0).toFullOrder(List.of(logs));
        var spruce = event(1, EventKind.REWARD_MULTIPLIER, 1.5, START, List.of("SPRUCE_LOG"), List.of());

        assertEquals(
                150.0,
                EventPreviews.preview(order, List.of(spruce), 0, 3)
                        .orElseThrow()
                        .net(),
                DELTA);
        assertEquals(
                1.5, EventPreviews.mark(order, List.of(spruce), 3).orElseThrow().multiplier(), DELTA);
    }

    private static FullOrder order() {
        return new SimpleOrder(UUID.randomUUID(), "test", 0)
                .toFullOrder(List.of(
                        new OrderContent(0, new ItemStack(Material.STONE), false, 10, 100.0),
                        new OrderContent(1, new ItemStack(Material.DIRT), false, 5, 50.0)));
    }
}
