/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.economyshopgui;

import de.eldoria.companies.components.price.ShopSide;
import me.gypopo.economyshopgui.util.EcoType;
import me.gypopo.economyshopgui.util.EconomyType;
import me.gypopo.economyshopgui.util.Transaction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyShopGuiPricesTest {
    @Test
    void onlyFinishedTransactionsOfOneShopItemAreRecorded() {
        assertTrue(EconomyShopGuiPrices.recorded(Transaction.Result.SUCCESS, 1));
        assertTrue(EconomyShopGuiPrices.recorded(Transaction.Result.SUCCESS_COMMANDS_EXECUTED, 1));
        assertFalse(EconomyShopGuiPrices.recorded(Transaction.Result.SUCCESS, 2));
        assertFalse(EconomyShopGuiPrices.recorded(Transaction.Result.INSUFFICIENT_FUNDS, 1));
        assertFalse(EconomyShopGuiPrices.recorded(null, 1));
    }

    @Test
    void transactionsAreSalesOrPurchasesAsEconomyShopGuiNamesThem() {
        assertEquals(ShopSide.SELL, EconomyShopGuiPrices.side(Transaction.Type.AUTO_SELL_CHEST));
        assertEquals(ShopSide.SELL, EconomyShopGuiPrices.side(Transaction.Type.QUICK_SELL));
        assertEquals(ShopSide.BUY, EconomyShopGuiPrices.side(Transaction.Type.QUICK_BUY));
        assertEquals(ShopSide.BUY, EconomyShopGuiPrices.side(Transaction.Type.BUY_STACKS_SCREEN));
    }

    @Test
    void onlyVaultPricesCount() {
        assertTrue(EconomyShopGuiPrices.vault(new EcoType(EconomyType.VAULT)));
        assertFalse(EconomyShopGuiPrices.vault(new EcoType(EconomyType.PLAYER_POINTS)));
        assertFalse(EconomyShopGuiPrices.vault(null));
    }
}
