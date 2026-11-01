/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.shopguiplus;

import de.eldoria.companies.components.price.ShopSide;
import net.brcdev.shopgui.economy.EconomyType;
import net.brcdev.shopgui.shop.ShopManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopGuiPlusPricesTest {
    @Test
    void sellingOneOrAllIsASale() {
        assertEquals(ShopSide.BUY, ShopGuiPlusPrices.side(ShopManager.ShopAction.BUY));
        assertEquals(ShopSide.SELL, ShopGuiPlusPrices.side(ShopManager.ShopAction.SELL));
        assertEquals(ShopSide.SELL, ShopGuiPlusPrices.side(ShopManager.ShopAction.SELL_ALL));
    }

    @Test
    void aShopPaysInVaultByNameOrThroughTheDefaultEconomy() {
        var vault = new Object();
        var points = new Object();

        assertTrue(ShopGuiPlusPrices.vault(EconomyType.VAULT, points, vault));
        assertTrue(ShopGuiPlusPrices.vault(null, vault, vault));
        assertFalse(ShopGuiPlusPrices.vault(null, points, vault));
        assertFalse(ShopGuiPlusPrices.vault(null, null, null));
        assertFalse(ShopGuiPlusPrices.vault(EconomyType.PLAYER_POINTS, vault, vault));
    }
}
