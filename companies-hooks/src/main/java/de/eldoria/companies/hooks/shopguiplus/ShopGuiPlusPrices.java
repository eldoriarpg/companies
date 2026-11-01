/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.shopguiplus;

import de.eldoria.companies.components.price.PriceRecorder;
import de.eldoria.companies.components.price.PriceSource;
import de.eldoria.companies.components.price.ShopOrigin;
import de.eldoria.companies.components.price.ShopQuote;
import de.eldoria.companies.components.price.ShopSide;
import de.eldoria.companies.components.price.ShopTrade;
import de.eldoria.companies.prices.PriceHookContext;
import net.brcdev.shopgui.ShopGuiPlugin;
import net.brcdev.shopgui.economy.EconomyType;
import net.brcdev.shopgui.event.ShopPostTransactionEvent;
import net.brcdev.shopgui.exception.shop.ShopsNotLoadedException;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.ShopManager;
import net.brcdev.shopgui.shop.ShopTransactionResult;
import net.brcdev.shopgui.shop.item.ShopItem;
import net.brcdev.shopgui.shop.item.ShopItemType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

/**
 * Prices of ShopGUI+: the buy and sell price of one of every item of its shops as server prices, read at every sample
 * once its shops are loaded, and its successful transactions as trades. Only item entries of shops paying in Vault
 * count, set on the shop or as ShopGUI+'s default economy; a price below zero means the item can not be bought or
 * sold that way. An item that fails to be read is skipped.
 */
public final class ShopGuiPlusPrices implements PriceSource, Listener {
    private final PriceHookContext context;
    private volatile PriceRecorder recorder = trade -> {};

    /**
     * @param context what the source gets when it is loaded
     */
    public ShopGuiPlusPrices(PriceHookContext context) {
        this.context = context;
    }

    @Override
    public String id() {
        return "shopguiplus";
    }

    @Override
    public String name() {
        return "ShopGUI+";
    }

    @Override
    public @Nullable Collection<ShopQuote> quotes() {
        var manager = ShopGuiPlugin.getInstance().getShopManager();
        if (!manager.areShopsLoaded()) return null;
        List<ShopQuote> quotes = new ArrayList<>();
        RuntimeException failure = null;
        var failed = 0;
        try {
            for (var shop : manager.getShops()) {
                if (!vault(shop)) continue;
                for (var item : shop.getShopItems()) {
                    try {
                        quote(item, quotes);
                    } catch (RuntimeException e) {
                        failed++;
                        failure = e;
                    }
                }
            }
        } catch (ShopsNotLoadedException e) {
            return null;
        }
        if (failure != null) {
            context.plugin()
                    .getLogger()
                    .log(Level.WARNING, "Could not read the prices of " + failed + " ShopGUI+ items", failure);
        }
        return quotes;
    }

    private static void quote(ShopItem item, List<ShopQuote> quotes) {
        if (item.getType() != ShopItemType.ITEM) return;
        var stack = one(item.getItem());
        if (stack == null) return;
        var buy = item.getBuyPriceForAmount(1);
        if (buy >= 0) quotes.add(new ShopQuote(stack, 1, buy, ShopSide.BUY, ShopOrigin.SERVER));
        var sell = item.getSellPriceForAmount(1);
        if (sell >= 0) quotes.add(new ShopQuote(stack, 1, sell, ShopSide.SELL, ShopOrigin.SERVER));
    }

    @Override
    public void record(PriceRecorder recorder) {
        this.recorder = recorder;
        var plugin = context.plugin();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Records a successful transaction.
     *
     * @param event the transaction
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTransaction(ShopPostTransactionEvent event) {
        var result = event.getResult();
        if (result == null || result.getResult() != ShopTransactionResult.ShopTransactionResultType.SUCCESS) return;
        var item = result.getShopItem();
        if (item == null || item.getType() != ShopItemType.ITEM || item.getShop() == null) return;
        if (!vault(item.getShop())) return;
        var stack = one(item.getItem());
        if (stack == null) return;
        recorder.trade(new ShopTrade(
                stack, result.getAmount(), result.getPrice(), side(result.getShopAction()), ShopOrigin.SERVER));
    }

    private static boolean vault(Shop shop) {
        var vault = ShopGuiPlugin.getInstance().getEconomyManager().getEconomyProvider(EconomyType.VAULT);
        return vault(shop.getEconomyType(), shop.getEconomyProvider(), vault);
    }

    /**
     * @param type     the economy a shop names, null when it names none
     * @param provider the economy provider the shop uses
     * @param vault    ShopGUI+'s Vault provider, null when Vault is not set up
     * @return whether the shop pays in Vault: it names Vault, or it names none and uses the Vault provider
     */
    static boolean vault(@Nullable EconomyType type, @Nullable Object provider, @Nullable Object vault) {
        if (type == EconomyType.VAULT) return true;
        return type == null && provider != null && provider == vault;
    }

    /**
     * @param action what the player did
     * @return whether the player sold or bought
     */
    static ShopSide side(ShopManager.ShopAction action) {
        return action == ShopManager.ShopAction.BUY ? ShopSide.BUY : ShopSide.SELL;
    }

    private static @Nullable ItemStack one(@Nullable ItemStack item) {
        if (item == null) return null;
        var single = item.clone();
        single.setAmount(1);
        return single;
    }
}
