/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.chestshop;

import com.Acrobot.Breeze.Utils.PriceUtil;
import de.eldoria.companies.components.price.ShopOrigin;
import de.eldoria.companies.components.price.ShopSide;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChestShopPricesTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aSignOffersToSellAndToBuyItsQuantity() {
        var offers = ChestShopPrices.offers(
                new ItemStack(Material.STONE, 3), 16, BigDecimal.TEN, new BigDecimal("4.5"), false);

        assertEquals(2, offers.size());
        var buy = offers.getFirst();
        assertEquals(ShopSide.BUY, buy.side());
        assertEquals(16, buy.amount());
        assertEquals(10, buy.price(), 1e-9);
        assertEquals(1, buy.item().getAmount());
        assertEquals(ShopOrigin.PLAYER, buy.origin());
        assertEquals(ShopSide.SELL, offers.get(1).side());
        assertEquals(4.5, offers.get(1).price(), 1e-9);
    }

    @Test
    void aTransactionIsOneTradeOfAllItsStacks() {
        var owner = UUID.randomUUID();
        var client = UUID.randomUUID();
        var stock = new ItemStack[] {new ItemStack(Material.STONE, 64), null, new ItemStack(Material.STONE, 16)};

        var bought = ChestShopPrices.trade(true, owner, client, stock, new BigDecimal("80"), false)
                .orElseThrow();
        var sold = ChestShopPrices.trade(false, null, client, stock, BigDecimal.ONE, true)
                .orElseThrow();

        assertEquals(ShopSide.BUY, bought.side());
        assertEquals(80, bought.amount());
        assertEquals(80, bought.price(), 1e-9);
        assertEquals(1, bought.item().getAmount());
        assertEquals(ShopOrigin.PLAYER, bought.origin());
        assertEquals(ShopSide.SELL, sold.side());
        assertEquals(ShopOrigin.ADMIN, sold.origin());
    }

    @Test
    void aTradeWithOnesOwnShopOrWithoutItemsIsNone() {
        var player = UUID.randomUUID();
        var stock = new ItemStack[] {new ItemStack(Material.STONE)};

        assertTrue(ChestShopPrices.trade(true, player, player, stock, BigDecimal.ONE, false)
                .isEmpty());
        assertTrue(ChestShopPrices.trade(true, null, player, new ItemStack[] {null}, BigDecimal.ONE, false)
                .isEmpty());
    }

    @Test
    void aSignWithoutAPriceForASideOrAnItemOffersNothingThere() {
        var sellOnly =
                ChestShopPrices.offers(new ItemStack(Material.STONE), 1, PriceUtil.NO_PRICE, BigDecimal.ONE, true);

        assertEquals(1, sellOnly.size());
        assertEquals(ShopSide.SELL, sellOnly.getFirst().side());
        assertEquals(ShopOrigin.ADMIN, sellOnly.getFirst().origin());
        assertTrue(ChestShopPrices.offers(new ItemStack(Material.STONE), 1, BigDecimal.ZERO, null, false)
                .isEmpty());
        assertTrue(ChestShopPrices.offers(null, 1, BigDecimal.ONE, BigDecimal.ONE, false)
                .isEmpty());
        assertTrue(ChestShopPrices.offers(new ItemStack(Material.STONE), 0, BigDecimal.ONE, BigDecimal.ONE, false)
                .isEmpty());
    }
}
