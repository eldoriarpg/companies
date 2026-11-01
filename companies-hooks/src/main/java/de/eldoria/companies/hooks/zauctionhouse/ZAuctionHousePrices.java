/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.zauctionhouse;

import de.eldoria.companies.components.price.PriceRecorder;
import de.eldoria.companies.components.price.PriceSource;
import de.eldoria.companies.components.price.ShopOrigin;
import de.eldoria.companies.components.price.ShopQuote;
import de.eldoria.companies.components.price.ShopSide;
import de.eldoria.companies.components.price.ShopTrade;
import de.eldoria.companies.prices.PriceHookContext;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.event.events.stats.AuctionItemPurchasedEvent;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Prices of zAuctionHouse: the items listed for sale as listings, and their purchases as trades, at what the buyer
 * paid, a buyer's tax included. A listing is one price for all its items; a listing of different items says nothing
 * of each, so it is left out, and so are rentals and bids, which are no plain sales. zAuctionHouse does not tell which
 * currency an economy pays in, so only the economies {@code zauctionhouseEconomies} in {@code prices.yml} names count,
 * every one while it names none. A purchase of a player's own listing is left out.
 * <p>
 * An auction house shared by several servers shows every server all listings, so only the primary samples them; other
 * servers sample none, which also clears what they stored as a former primary. A purchase is recorded on the server it
 * was made on.
 */
public final class ZAuctionHousePrices implements PriceSource, Listener {
    private final PriceHookContext context;
    private volatile PriceRecorder recorder = trade -> {};

    /**
     * @param context what the source gets when it is loaded
     */
    public ZAuctionHousePrices(PriceHookContext context) {
        this.context = context;
    }

    @Override
    public String id() {
        return "zauctionhouse";
    }

    @Override
    public String name() {
        return "zAuctionHouse";
    }

    @Override
    public @Nullable Collection<ShopQuote> quotes() {
        if (!context.primary()) return List.of();
        if (!(context.plugin().getServer().getPluginManager().getPlugin("zAuctionHouse")
                instanceof AuctionPlugin plugin)) {
            return null;
        }
        List<ShopQuote> quotes = new ArrayList<>();
        for (var item : plugin.getAuctionManager().getItems(StorageType.LISTED)) {
            if (!(item instanceof AuctionItem auction) || !auction.isActivelyListed()) continue;
            if (!counts(auction)) continue;
            quote(auction.getItemStacks(), auction.getPrice()).ifPresent(quotes::add);
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
     * Records a purchase, after it went through.
     *
     * @param event the purchase
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPurchase(AuctionItemPurchasedEvent event) {
        var item = event.getItem();
        if (item == null || !counts(item)) return;
        if (own(item.getSellerUniqueId(), event.getBuyer().getUniqueId())) return;
        quote(item.getItemStacks(), event.getBuyerPays())
                .ifPresent(made -> recorder.trade(
                        new ShopTrade(made.item(), made.amount(), made.price(), made.side(), made.origin())));
    }

    private boolean counts(AuctionItem item) {
        var economy = item.getEconomyName();
        return economy != null && context.settings().get().zauctionhouseEconomy(economy);
    }

    /**
     * @param seller the seller of a listing
     * @param buyer  who bought it
     * @return whether the buyer is the seller
     */
    static boolean own(@Nullable UUID seller, UUID buyer) {
        return buyer.equals(seller);
    }

    /**
     * A listing as an offer to sell its items.
     *
     * @param stacks the items of the listing
     * @param price  the price of all of them
     * @return the offer, empty when the listing holds no item, different items or no price
     */
    static Optional<ShopQuote> quote(@Nullable List<ItemStack> stacks, @Nullable BigDecimal price) {
        if (stacks == null || stacks.isEmpty() || price == null) return Optional.empty();
        ItemStack first = null;
        var amount = 0;
        for (var stack : stacks) {
            if (stack == null) continue;
            if (first == null) first = stack;
            else if (!first.isSimilar(stack)) return Optional.empty();
            amount += stack.getAmount();
        }
        if (first == null) return Optional.empty();
        var single = first.clone();
        single.setAmount(1);
        return Optional.of(new ShopQuote(single, amount, price.doubleValue(), ShopSide.BUY, ShopOrigin.PLAYER));
    }
}
