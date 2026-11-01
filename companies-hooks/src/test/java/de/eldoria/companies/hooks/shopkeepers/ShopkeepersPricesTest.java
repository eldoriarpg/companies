/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.shopkeepers;

import de.eldoria.companies.components.price.ShopOrigin;
import de.eldoria.companies.components.price.ShopSide;
import de.eldoria.companies.components.price.ShopTrade;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopkeepersPricesTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void anOfferIsWorthItsCurrencyItemsTimesTheirValue() {
        var offer = ShopkeepersPrices.quote(new ItemStack(Material.DIRT, 16), 16, 2, ShopSide.BUY, 12.5)
                .orElseThrow();

        assertEquals(16, offer.amount());
        assertEquals(25, offer.price(), 1e-9);
        assertEquals(1, offer.item().getAmount());
        assertEquals(ShopOrigin.PLAYER, offer.origin());
    }

    @Test
    void sellingShopsSellAndBuyingShopsBuyButNotForTheirOwner() {
        assertEquals(ShopSide.BUY, ShopkeepersPrices.side(true, false, false).orElseThrow());
        assertEquals(ShopSide.SELL, ShopkeepersPrices.side(false, true, false).orElseThrow());
        assertTrue(ShopkeepersPrices.side(true, false, true).isEmpty());
        assertTrue(ShopkeepersPrices.side(false, false, false).isEmpty());
    }

    @Test
    void theTradesOfAShiftClickAddUp() {
        var trade = new ShopTrade(new ItemStack(Material.DIRT), 16, 25, ShopSide.BUY, ShopOrigin.PLAYER);

        var sum = ShopkeepersPrices.sum(trade, trade);

        assertEquals(32, sum.amount());
        assertEquals(50, sum.price(), 1e-9);
        assertEquals(ShopSide.BUY, sum.side());
    }

    @Test
    void withoutAValueOrAPriceThereIsNoOffer() {
        assertTrue(ShopkeepersPrices.quote(new ItemStack(Material.DIRT), 1, 2, ShopSide.BUY, 0)
                .isEmpty());
        assertTrue(ShopkeepersPrices.quote(new ItemStack(Material.DIRT), 1, 0, ShopSide.BUY, 1)
                .isEmpty());
        assertTrue(ShopkeepersPrices.quote(null, 1, 2, ShopSide.BUY, 1).isEmpty());
    }
}
