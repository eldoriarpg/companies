/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.data.wrapper.order.MaterialPrice;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("UnstableApiUsage")
class OrderBuilderTest {
    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    private final OrderBuilder builder = new OrderBuilder(UUID.randomUUID(), "order");

    private static ComponentStack plain(Material material) {
        return new ComponentStack(material);
    }

    private static ComponentStack book(int sharpness) {
        var stack = plain(Material.ENCHANTED_BOOK);
        stack.setData(
                DataComponentTypes.STORED_ENCHANTMENTS,
                ItemEnchantments.itemEnchantments()
                        .add(Enchantment.SHARPNESS, sharpness)
                        .build());
        return stack;
    }

    @Test
    void totalIsUnitPriceTimesAmountRoundedToCents() {
        assertEquals(29.0, OrderBuilder.total(0.29, 100));
        assertEquals(0.38, OrderBuilder.total(0.125, 3));
        assertEquals(0.33, OrderBuilder.total(1.0 / 9, 3));
        assertEquals(0.0, OrderBuilder.total(0.0, 64));
    }

    @Test
    void changingTheAmountKeepsTheUnitPriceAndScalesTheTotal() {
        builder.add(plain(Material.STONE), false, 10, 1.5);

        assertTrue(builder.amount(1, 20));

        var line = builder.line(1).orElseThrow();
        assertEquals(1.5, line.unitPrice());
        assertEquals(20, line.amount());
        assertEquals(30.0, line.total());
        assertEquals(30.0, builder.price());
    }

    @Test
    void changingTheUnitPriceChangesTheTotal() {
        builder.add(plain(Material.STONE), false, 4, 1.0);

        assertTrue(builder.unitPrice(1, 2.25));

        assertEquals(9.0, builder.line(1).orElseThrow().total());
    }

    @Test
    void similarMaterialLinesMergeAndTakeTheNewUnitPrice() {
        assertEquals(1, builder.add(plain(Material.STONE), false, 10, 1.0));
        assertEquals(1, builder.add(plain(Material.STONE), false, 5, 2.0));

        assertEquals(1, builder.lineCount());
        var line = builder.line(1).orElseThrow();
        assertEquals(15, line.amount());
        assertEquals(2.0, line.unitPrice());
        assertEquals(30.0, line.total());
    }

    @Test
    void exactAndMaterialLinesOfOneTypeStaySeparate() {
        assertEquals(1, builder.add(plain(Material.ENCHANTED_BOOK), false, 1, 5.0));
        assertEquals(2, builder.add(book(5), true, 1, 50.0));
        assertEquals(3, builder.add(book(4), true, 1, 30.0));
        assertEquals(2, builder.add(book(5), true, 2, 40.0));

        assertEquals(3, builder.lineCount());
        assertFalse(builder.line(1).orElseThrow().exact());
        assertTrue(builder.line(2).orElseThrow().exact());
        assertEquals(3, builder.line(2).orElseThrow().amount());
        assertEquals(120.0, builder.line(2).orElseThrow().total());
    }

    @Test
    void removingALineRenumbersTheFollowingLines() {
        builder.add(plain(Material.STONE), false, 1, 1.0);
        builder.add(plain(Material.DIRT), false, 2, 1.0);

        assertTrue(builder.remove(1));
        assertFalse(builder.remove(2));
        assertFalse(builder.amount(0, 5));
        assertFalse(builder.unitPrice(3, 5));

        assertEquals(Material.DIRT, builder.line(1).orElseThrow().template().getType());
    }

    @Test
    void amountWithoutLeavesOutOneLine() {
        builder.add(plain(Material.STONE), false, 10, 1.0);
        builder.add(plain(Material.DIRT), false, 5, 1.0);

        assertEquals(15, builder.amount());
        assertEquals(5, builder.amountWithout(1));
        assertEquals(15, builder.amountWithout(3));
    }

    @Test
    void theBuiltOrderKeepsLinePositionsAndRoundedTotals() {
        builder.add(plain(Material.STONE), false, 3, 0.125);
        builder.add(book(5), true, 1, 50.0);

        var order = builder.build(2.0);

        List<OrderContent> contents = order.contents();
        assertEquals(2, contents.size());
        assertEquals(0, contents.get(0).line());
        assertEquals(0.38, contents.get(0).price());
        assertFalse(contents.get(0).exact());
        assertEquals(1, contents.get(1).line());
        assertTrue(contents.get(1).exact());
        assertEquals(50.0, contents.get(1).price());
        assertEquals(2.0, order.fee());
        assertEquals(50.38, builder.price());
    }

    @Test
    void theMarketPriceIsOfferedOnlyForMaterialLinesWithData() {
        var withData = new MaterialPrice("stone", 2.0, 1.0, 3.0);
        var withoutData = new MaterialPrice("stone");

        assertTrue(OrderBuilder.offeredMarketPrice(false, withData).isPresent());
        assertFalse(OrderBuilder.offeredMarketPrice(true, withData).isPresent());
        assertFalse(OrderBuilder.offeredMarketPrice(false, withoutData).isPresent());
    }
}
