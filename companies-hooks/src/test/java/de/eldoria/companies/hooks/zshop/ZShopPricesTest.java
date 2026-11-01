/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.zshop;

import de.eldoria.companies.components.price.ShopSide;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZShopPricesTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aSaleOfAWholeInventoryIsOneTradePerItem() {
        var stone = new ItemStack(Material.STONE);
        var dirt = new ItemStack(Material.DIRT);

        var trades = ZShopPrices.merged(List.of(
                new ZShopPrices.Sold(stone, 64, 6.4),
                new ZShopPrices.Sold(dirt, 10, 1),
                new ZShopPrices.Sold(stone.clone(), 32, 3.2)));

        assertEquals(2, trades.size());
        assertEquals(Material.STONE, trades.getFirst().item().getType());
        assertEquals(96, trades.getFirst().amount());
        assertEquals(9.6, trades.getFirst().price(), 1e-9);
        assertEquals(ShopSide.SELL, trades.getFirst().side());
        assertEquals(10, trades.get(1).amount());
    }
}
