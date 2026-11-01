/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.zauctionhouse;

import de.eldoria.companies.components.price.ShopOrigin;
import de.eldoria.companies.components.price.ShopSide;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZAuctionHousePricesTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aListingIsOnePriceForAllItsItems() {
        var offer = ZAuctionHousePrices.quote(
                        Arrays.asList(new ItemStack(Material.DIAMOND, 32), null, new ItemStack(Material.DIAMOND, 8)),
                        new BigDecimal("400"))
                .orElseThrow();

        assertEquals(40, offer.amount());
        assertEquals(400, offer.price(), 1e-9);
        assertEquals(1, offer.item().getAmount());
        assertEquals(ShopSide.BUY, offer.side());
        assertEquals(ShopOrigin.PLAYER, offer.origin());
    }

    @Test
    void aListingOfDifferentItemsOrWithoutItemsOrPriceSaysNothing() {
        assertTrue(ZAuctionHousePrices.quote(
                        List.of(new ItemStack(Material.DIAMOND), new ItemStack(Material.EMERALD)), BigDecimal.TEN)
                .isEmpty());
        assertTrue(ZAuctionHousePrices.quote(List.of(), BigDecimal.TEN).isEmpty());
        assertTrue(ZAuctionHousePrices.quote(List.of(new ItemStack(Material.DIAMOND)), null)
                .isEmpty());
    }

    @Test
    void buyingOnesOwnListingIsNoTrade() {
        var player = UUID.randomUUID();

        assertTrue(ZAuctionHousePrices.own(player, player));
        assertFalse(ZAuctionHousePrices.own(UUID.randomUUID(), player));
        assertFalse(ZAuctionHousePrices.own(null, player));
    }
}
