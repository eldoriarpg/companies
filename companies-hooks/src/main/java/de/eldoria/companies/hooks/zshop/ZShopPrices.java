/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.zshop;

import de.eldoria.companies.components.price.PriceRecorder;
import de.eldoria.companies.components.price.PriceSource;
import de.eldoria.companies.components.price.ShopOrigin;
import de.eldoria.companies.components.price.ShopQuote;
import de.eldoria.companies.components.price.ShopSide;
import de.eldoria.companies.components.price.ShopTrade;
import de.eldoria.companies.prices.PriceHookContext;
import fr.maxlego08.shop.api.ShopManager;
import fr.maxlego08.shop.api.buttons.ItemButton;
import fr.maxlego08.shop.api.event.events.ZShopBuyEvent;
import fr.maxlego08.shop.api.event.events.ZShopSellAllEvent;
import fr.maxlego08.shop.api.event.events.ZShopSellEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;

/**
 * Prices of zShop: the unit buy and sell price of every item button as server prices, and its purchases and sales as
 * trades. zShop does not tell which currency an economy pays in, so only the economies {@code zshopEconomies} in
 * {@code prices.yml} names count, every one while it names none. Buttons of mob spawners are left out, as their item
 * says nothing of the mob, and so is the buy side of buttons that run commands instead of giving their item.
 * <p>
 * zShop builds the item of a button for a player only. A sample builds them for a player online and keeps them per
 * button; while nobody is online it uses those, and while a button counted has never been built, the sample is
 * skipped and the stored one kept. A sale of a whole inventory is recorded as one trade per item; zShop's sell limits
 * are applied after it is announced, so a sale cut by a limit counts as asked for.
 */
public final class ZShopPrices implements PriceSource, Listener {
    private final PriceHookContext context;
    private final Map<ItemButton, ItemStack> built = Collections.synchronizedMap(new WeakHashMap<>());
    private volatile PriceRecorder recorder = trade -> {};

    /**
     * @param context what the source gets when it is loaded
     */
    public ZShopPrices(PriceHookContext context) {
        this.context = context;
    }

    @Override
    public String id() {
        return "zshop";
    }

    @Override
    public String name() {
        return "zShop";
    }

    @Override
    public @Nullable Collection<ShopQuote> quotes() {
        var manager = context.plugin().getServer().getServicesManager().load(ShopManager.class);
        if (manager == null) return null;
        var player = context.plugin().getServer().getOnlinePlayers().stream()
                .findFirst()
                .orElse(null);
        List<ShopQuote> quotes = new ArrayList<>();
        RuntimeException failure = null;
        var failed = 0;
        for (var button : manager.getItemButtons()) {
            try {
                if (!counts(button)) continue;
                var stack = player == null ? built.get(button) : item(button, player);
                if (stack == null && player == null) return null;
                if (stack == null) continue;
                if (button.canBuy() && button.giveItem()) {
                    quotes.add(new ShopQuote(stack, 1, button.getUnitBuyPrice(), ShopSide.BUY, ShopOrigin.SERVER));
                }
                if (button.canSell()) {
                    quotes.add(new ShopQuote(stack, 1, button.getUnitSellPrice(), ShopSide.SELL, ShopOrigin.SERVER));
                }
            } catch (RuntimeException e) {
                failed++;
                failure = e;
            }
        }
        if (failure != null) {
            context.plugin()
                    .getLogger()
                    .log(Level.WARNING, "Could not read the prices of " + failed + " zShop buttons", failure);
        }
        return quotes;
    }

    @Override
    public void record(PriceRecorder recorder) {
        this.recorder = recorder;
        var plugin = context.plugin();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Records a purchase of a button that gives its item.
     *
     * @param event the purchase
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBuy(ZShopBuyEvent event) {
        var button = event.getItemButton();
        if (button == null || !button.giveItem()) return;
        trade(button, event.getPlayer(), event.getAmount(), event.getPrice(), ShopSide.BUY);
    }

    /**
     * Records a sale.
     *
     * @param event the sale
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSell(ZShopSellEvent event) {
        trade(event.getItemButton(), event.getPlayer(), event.getAmount(), event.getPrice(), ShopSide.SELL);
    }

    /**
     * Records a sale of a whole inventory, one trade per item.
     *
     * @param event the sale
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSellAll(ZShopSellAllEvent event) {
        List<Sold> sold = new ArrayList<>();
        for (var action : event.getShopActions()) {
            var button = action.getItemButton();
            if (button == null || !counts(button)) continue;
            var stack = one(action.getItemStack());
            if (stack != null) sold.add(new Sold(stack, action.getTotalAmount(), action.getPrice()));
        }
        merged(sold).forEach(recorder::trade);
    }

    /**
     * Sums up what a sale of a whole inventory sold of each item, as zShop announces it per slot.
     *
     * @param sold what was sold per slot
     * @return one sale per item, in the order the items first came
     */
    static List<ShopTrade> merged(List<Sold> sold) {
        Map<ItemStack, Sold> items = new LinkedHashMap<>();
        for (var slot : sold) {
            items.merge(
                    slot.item(),
                    slot,
                    (first, next) ->
                            new Sold(first.item(), first.amount() + next.amount(), first.price() + next.price()));
        }
        return items.values().stream()
                .map(item -> new ShopTrade(item.item(), item.amount(), item.price(), ShopSide.SELL, ShopOrigin.SERVER))
                .toList();
    }

    private void trade(@Nullable ItemButton button, Player player, int amount, double price, ShopSide side) {
        if (button == null || !counts(button)) return;
        var stack = item(button, player);
        if (stack == null) return;
        recorder.trade(new ShopTrade(stack, amount, price, side, ShopOrigin.SERVER));
    }

    private boolean counts(ItemButton button) {
        var economy = button.getEconomy();
        if (economy == null || !context.settings().get().zshopEconomy(economy.getName())) return false;
        var mob = button.getMob();
        return mob == null || mob.isBlank();
    }

    private @Nullable ItemStack item(ItemButton button, Player player) {
        var menuItem = button.getItemStack();
        if (menuItem == null) return null;
        var stack = one(menuItem.build(player, false));
        if (stack != null) built.put(button, stack);
        return stack;
    }

    private static @Nullable ItemStack one(@Nullable ItemStack item) {
        if (item == null) return null;
        var single = item.clone();
        single.setAmount(1);
        return single;
    }

    /**
     * What a sale sold of one slot.
     *
     * @param item   one of the item
     * @param amount how many were sold
     * @param price  what they were sold for
     */
    record Sold(ItemStack item, int amount, double price) {}
}
