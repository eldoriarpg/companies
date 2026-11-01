/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.shopkeepers;

import com.nisovin.shopkeepers.api.ShopkeepersAPI;
import com.nisovin.shopkeepers.api.events.ShopkeeperTradeCompletedEvent;
import com.nisovin.shopkeepers.api.shopkeeper.offers.PriceOffer;
import com.nisovin.shopkeepers.api.shopkeeper.player.PlayerShopkeeper;
import com.nisovin.shopkeepers.api.shopkeeper.player.buy.BuyingPlayerShopkeeper;
import com.nisovin.shopkeepers.api.shopkeeper.player.sell.SellingPlayerShopkeeper;
import de.eldoria.companies.components.price.PriceRecorder;
import de.eldoria.companies.components.price.PriceSource;
import de.eldoria.companies.components.price.ShopOrigin;
import de.eldoria.companies.components.price.ShopQuote;
import de.eldoria.companies.components.price.ShopSide;
import de.eldoria.companies.components.price.ShopTrade;
import de.eldoria.companies.prices.PriceHookContext;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Prices of Shopkeepers: the offers of player shops that sell or buy items for currency as listings, and their trades.
 * Shopkeepers prices are counts of base currency items, so they count only once {@code shopkeepersCurrencyValue} in
 * {@code prices.yml} says what one is worth. Admin shopkeepers trade item for item and say nothing of money, so they
 * are left out, and so are trades of a player with their own shop; members a shop lets in count as anyone.
 * Shopkeepers announces every single trade of a shift-click; the trades of a player with a shop at one offer within
 * one tick are recorded as one, as other shop plugins count a shift-click.
 */
public final class ShopkeepersPrices implements PriceSource, Listener {
    private final PriceHookContext context;
    private final Map<Pending, ShopTrade> pending = new LinkedHashMap<>();
    private volatile PriceRecorder recorder = trade -> {};

    /**
     * @param context what the source gets when it is loaded
     */
    public ShopkeepersPrices(PriceHookContext context) {
        this.context = context;
    }

    @Override
    public String id() {
        return "shopkeepers";
    }

    @Override
    public String name() {
        return "Shopkeepers";
    }

    @Override
    public Collection<ShopQuote> quotes() {
        var value = context.settings().get().shopkeepersCurrencyValue();
        if (value <= 0) return List.of();
        List<ShopQuote> quotes = new ArrayList<>();
        for (var shop : ShopkeepersAPI.getShopkeeperRegistry().getAllPlayerShopkeepers()) {
            if (shop instanceof SellingPlayerShopkeeper selling) {
                for (var offer : selling.getOffers())
                    offer(offer, ShopSide.BUY, value).ifPresent(quotes::add);
            } else if (shop instanceof BuyingPlayerShopkeeper buying) {
                for (var offer : buying.getOffers())
                    offer(offer, ShopSide.SELL, value).ifPresent(quotes::add);
            }
        }
        return quotes;
    }

    @Override
    public void record(PriceRecorder recorder) {
        this.recorder = recorder;
        var plugin = context.plugin();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (context.settings().get().shopkeepersCurrencyValue() <= 0) {
            plugin.getLogger()
                    .info("Shopkeepers prices are not read until shopkeepersCurrencyValue in prices.yml says what one"
                            + " currency item is worth.");
        }
    }

    /**
     * Records a trade with a player shop, priced by the offer it was made at.
     *
     * @param event the trade
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTrade(ShopkeeperTradeCompletedEvent event) {
        var value = context.settings().get().shopkeepersCurrencyValue();
        if (value <= 0) return;
        var trade = event.getCompletedTrade();
        var shop = event.getShopkeeper();
        var player = trade.getPlayer();
        var recipe = trade.getTradingRecipe();
        var side = side(
                shop instanceof SellingPlayerShopkeeper,
                shop instanceof BuyingPlayerShopkeeper,
                shop instanceof PlayerShopkeeper owned && owned.isOwner(player));
        if (side.isEmpty()) return;
        var offer = side.get() == ShopSide.BUY
                ? ((SellingPlayerShopkeeper) shop).getOffer(recipe.getResultItem())
                : ((BuyingPlayerShopkeeper) shop).getOffer(recipe.getItem1());
        offer(offer, side.get(), value)
                .ifPresent(made -> add(
                        new Pending(player.getUniqueId(), shop.getUniqueId(), made),
                        new ShopTrade(made.item(), made.amount(), made.price(), made.side(), made.origin())));
    }

    private void add(Pending key, ShopTrade trade) {
        boolean first;
        synchronized (pending) {
            first = pending.isEmpty();
            pending.merge(key, trade, ShopkeepersPrices::sum);
        }
        if (first) context.plugin().getServer().getScheduler().runTask(context.plugin(), this::flush);
    }

    private void flush() {
        List<ShopTrade> trades;
        synchronized (pending) {
            trades = List.copyOf(pending.values());
            pending.clear();
        }
        trades.forEach(recorder::trade);
    }

    /**
     * @param first a trade
     * @param next  another trade at the same offer
     * @return both as one trade
     */
    static ShopTrade sum(ShopTrade first, ShopTrade next) {
        return new ShopTrade(
                first.item(),
                first.amount() + next.amount(),
                first.price() + next.price(),
                first.side(),
                first.origin());
    }

    /**
     * Which way a trade with a shopkeeper went: a selling shop sells its result item to the player, a buying shop buys
     * the player's first item.
     *
     * @param selling whether the shop is a selling player shop
     * @param buying  whether the shop is a buying player shop
     * @param owner   whether the player owns the shop
     * @return the side of the player, empty for other shops and for the owner
     */
    static Optional<ShopSide> side(boolean selling, boolean buying, boolean owner) {
        if (owner) return Optional.empty();
        if (selling) return Optional.of(ShopSide.BUY);
        if (buying) return Optional.of(ShopSide.SELL);
        return Optional.empty();
    }

    private static Optional<ShopQuote> offer(@Nullable PriceOffer offer, ShopSide side, double value) {
        if (offer == null) return Optional.empty();
        var item = offer.getItem().copy();
        return quote(item, item.getAmount(), offer.getPrice(), side, value);
    }

    /**
     * An offer of a player shop in money.
     *
     * @param item   the item offered
     * @param amount how many items the offer is for
     * @param price  how many currency items it costs or pays
     * @param side   whether players buy or sell at it
     * @param value  the money one currency item is worth
     * @return the offer, empty when it is for no item or no currency
     */
    static Optional<ShopQuote> quote(@Nullable ItemStack item, int amount, int price, ShopSide side, double value) {
        if (item == null || amount < 1 || price < 1 || value <= 0) return Optional.empty();
        var single = item.clone();
        single.setAmount(1);
        return Optional.of(new ShopQuote(single, amount, price * value, side, ShopOrigin.PLAYER));
    }

    private record Pending(UUID player, UUID shop, ShopQuote offer) {}
}
