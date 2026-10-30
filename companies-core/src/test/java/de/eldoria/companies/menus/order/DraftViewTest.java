/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.order;

import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.configuration.elements.ordersettings.OrderFee;
import de.eldoria.companies.data.wrapper.event.EventKind;
import de.eldoria.companies.data.wrapper.event.EventState;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.data.wrapper.order.MaterialPrice;
import de.eldoria.companies.orders.OrderBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DraftViewTest {
    private static final Function<String, MaterialPrice> NO_MARKET = MaterialPrice::new;

    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    private final OrderBuilder draft = new OrderBuilder(UUID.randomUUID(), "Wood");

    private static OrderSettings settings(int maxLines, int maxItems) {
        var settings = new OrderSettings();
        settings.maxMaterials(maxLines);
        settings.maxItems(maxItems);
        return settings;
    }

    @Test
    void linesAreNumberedInTheirOrderWithTheirSums() {
        draft.add(new ItemStack(Material.OAK_LOG), false, 10, 1.5);
        draft.add(new ItemStack(Material.STONE), false, 3, 0.125);

        var view = DraftView.of(draft, settings(5, 100), NO_MARKET);

        assertEquals("Wood", view.name());
        assertEquals(
                List.of(1, 2), view.lines().stream().map(DraftView.Line::number).toList());
        var stone = view.line(2).orElseThrow();
        assertEquals(Material.STONE, stone.template().getType());
        assertEquals(3, stone.amount());
        assertEquals(0.125, stone.unitPrice());
        assertEquals(0.38, stone.total());
        assertEquals(13, view.items());
        assertEquals(15.38, view.price());
        assertEquals(Optional.empty(), view.line(3));
        assertEquals(Optional.empty(), view.line(0));
    }

    @Test
    void onlyMaterialLinesWithMarketDataOfferTheMarketPrice() {
        draft.add(new ItemStack(Material.OAK_LOG), false, 1, 1);
        draft.add(new ItemStack(Material.STONE), false, 1, 1);
        draft.add(new ItemStack(Material.OAK_LOG), true, 1, 1);
        Function<String, MaterialPrice> prices = material ->
                material.equals("oak_log") ? new MaterialPrice(material, 2, 1, 3) : new MaterialPrice(material);

        var view = DraftView.of(draft, settings(5, 100), prices);

        assertEquals(2.0, view.line(1).orElseThrow().market().orElseThrow().avgPrice());
        assertTrue(view.line(2).orElseThrow().market().isEmpty());
        assertTrue(view.line(3).orElseThrow().market().isEmpty());
    }

    @Test
    void addingIsOfferedBelowBothLimits() {
        draft.add(new ItemStack(Material.OAK_LOG), false, 4, 1);
        assertTrue(DraftView.of(draft, settings(2, 10), NO_MARKET).canAdd());

        draft.add(new ItemStack(Material.STONE), false, 1, 1);
        assertFalse(DraftView.of(draft, settings(2, 10), NO_MARKET).canAdd());
        assertFalse(DraftView.of(draft, settings(5, 5), NO_MARKET).canAdd());
        assertTrue(DraftView.of(draft, settings(5, 6), NO_MARKET).canAdd());
    }

    @Test
    void slidersEndAtTheItemLimit() {
        draft.add(new ItemStack(Material.OAK_LOG), false, 4, 1);
        draft.add(new ItemStack(Material.STONE), false, 6, 1);

        var view = DraftView.of(draft, settings(5, 20), NO_MARKET);

        assertEquals(10, view.remainingItems());
        assertEquals(14, view.maxAmount(view.line(1).orElseThrow()));
        assertEquals(16, view.maxAmount(view.line(2).orElseThrow()));
        var full = DraftView.of(draft, settings(5, 5), NO_MARKET);
        assertEquals(0, full.remainingItems());
        assertEquals(1, full.maxAmount(full.line(2).orElseThrow()));
    }

    @Test
    void feeIsTakenFromTheSettings() {
        draft.add(new ItemStack(Material.OAK_LOG), false, 10, 1);

        var view = DraftView.of(draft, settings(5, 100), NO_MARKET);

        assertEquals(0.0, view.fee());
        assertEquals(10.0, view.total());
    }

    @Test
    void theFeeCountsTheFeeReductionsOfTheEventsRunningNow() {
        draft.add(new ItemStack(Material.OAK_LOG), false, 10, 10.0);
        var settings = new OrderSettings() {
            @Override
            public OrderFee fee() {
                return new OrderFee(0, 10, 0, 0);
            }
        };
        var now = LocalDateTime.of(2026, 10, 30, 12, 0);
        var half = new ServerEvent(
                1,
                "Cheap",
                EventKind.FEE_REDUCTION,
                50,
                List.of(),
                List.of(),
                now,
                now.plusDays(1),
                null,
                null,
                null,
                0,
                EventState.ACTIVE,
                null,
                "Nora",
                now,
                null,
                null);

        assertEquals(10.0, DraftView.of(draft, settings, NO_MARKET, false).fee());
        assertEquals(
                5.0,
                DraftView.of(draft, settings, NO_MARKET, false, List.of(half)).fee());
    }
}
