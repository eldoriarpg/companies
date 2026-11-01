/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.economyshopgui;

import de.eldoria.companies.components.price.PriceRecorder;
import de.eldoria.companies.components.price.PriceSource;
import de.eldoria.companies.components.price.ShopOrigin;
import de.eldoria.companies.components.price.ShopQuote;
import de.eldoria.companies.components.price.ShopSide;
import de.eldoria.companies.components.price.ShopTrade;
import de.eldoria.companies.prices.PriceHookContext;
import me.gypopo.economyshopgui.api.EconomyShopGUIHook;
import me.gypopo.economyshopgui.api.events.PostTransactionEvent;
import me.gypopo.economyshopgui.objects.ShopItem;
import me.gypopo.economyshopgui.util.EcoType;
import me.gypopo.economyshopgui.util.EconomyType;
import me.gypopo.economyshopgui.util.Transaction;
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
 * Prices of EconomyShopGUI, free and Premium: the buy and sell price of one of every item of its shop sections as
 * server prices, read at every sample once its shops are loaded, and its completed transactions as trades. Only
 * items priced in Vault count; display items, items that failed to load and transactions of several items at once,
 * such as selling a whole inventory, are left out, as those give one price for many different items. An item that
 * fails to be read is skipped.
 */
public final class EconomyShopGuiPrices implements PriceSource, Listener {
    private final PriceHookContext context;
    private volatile PriceRecorder recorder = trade -> {};

    /**
     * @param context what the source gets when it is loaded
     */
    public EconomyShopGuiPrices(PriceHookContext context) {
        this.context = context;
    }

    @Override
    public String id() {
        return "economyshopgui";
    }

    @Override
    public String name() {
        return "EconomyShopGUI";
    }

    @Override
    public @Nullable Collection<ShopQuote> quotes() {
        var sections = EconomyShopGUIHook.getSections();
        if (sections == null || sections.isEmpty()) return null;
        List<ShopQuote> quotes = new ArrayList<>();
        RuntimeException failure = null;
        var failed = 0;
        for (var section : sections.values()) {
            for (var item : section.getShopItems()) {
                try {
                    quote(item, quotes);
                } catch (RuntimeException e) {
                    failed++;
                    failure = e;
                }
            }
        }
        if (failure != null) {
            context.plugin()
                    .getLogger()
                    .log(Level.WARNING, "Could not read the prices of " + failed + " EconomyShopGUI items", failure);
        }
        return quotes;
    }

    private static void quote(ShopItem item, List<ShopQuote> quotes) {
        if (item.isDisplayItem() || item.hasItemError() || !vault(item.getEcoType())) return;
        var stack = one(item.getItemToGive());
        if (stack == null) return;
        if (EconomyShopGUIHook.isBuyAble(item)) {
            quotes.add(new ShopQuote(
                    stack, 1, EconomyShopGUIHook.getItemBuyPrice(item, 1), ShopSide.BUY, ShopOrigin.SERVER));
        }
        if (EconomyShopGUIHook.isSellAble(item)) {
            var sell = EconomyShopGUIHook.getItemSellPrice(item, stack);
            if (sell != null) quotes.add(new ShopQuote(stack, 1, sell, ShopSide.SELL, ShopOrigin.SERVER));
        }
    }

    @Override
    public void record(PriceRecorder recorder) {
        this.recorder = recorder;
        var plugin = context.plugin();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Records a completed transaction of one shop item.
     *
     * @param event the transaction
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTransaction(PostTransactionEvent event) {
        var items = event.getItems();
        if (!recorded(event.getTransactionResult(), items == null ? 1 : items.size())) return;
        var item = event.getShopItem();
        if (item == null || item.isDisplayItem() || item.hasItemError() || !vault(item.getEcoType())) return;
        var stack = one(item.getItemToGive());
        if (stack == null) return;
        recorder.trade(new ShopTrade(
                stack, event.getAmount(), event.getPrice(), side(event.getTransactionType()), ShopOrigin.SERVER));
    }

    /**
     * @param result the result of a transaction
     * @param items  how many different shop items it moved
     * @return whether it is recorded: it went through and moved one shop item
     */
    static boolean recorded(@Nullable Transaction.Result result, int items) {
        if (result != Transaction.Result.SUCCESS && result != Transaction.Result.SUCCESS_COMMANDS_EXECUTED) {
            return false;
        }
        return items <= 1;
    }

    /**
     * @param type the kind of transaction
     * @return whether the player sold or bought, as EconomyShopGUI names it
     */
    static ShopSide side(Transaction.Type type) {
        return Transaction.Mode.getFromType(type) == Transaction.Mode.SELL ? ShopSide.SELL : ShopSide.BUY;
    }

    /**
     * @param eco the economy of a shop item
     * @return whether it pays in Vault, the server's main currency
     */
    static boolean vault(@Nullable EcoType eco) {
        return eco != null && eco.getType() == EconomyType.VAULT;
    }

    private static @Nullable ItemStack one(@Nullable ItemStack item) {
        if (item == null) return null;
        var single = item.clone();
        single.setAmount(1);
        return single;
    }
}
