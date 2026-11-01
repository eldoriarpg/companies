/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.chestshop;

import com.Acrobot.Breeze.Utils.PriceUtil;
import com.Acrobot.ChestShop.ChestShop;
import com.Acrobot.ChestShop.Events.ItemParseEvent;
import com.Acrobot.ChestShop.Events.ShopCreatedEvent;
import com.Acrobot.ChestShop.Events.ShopDestroyedEvent;
import com.Acrobot.ChestShop.Events.TransactionEvent;
import com.Acrobot.ChestShop.Signs.ChestShopSign;
import de.eldoria.companies.components.price.PriceRecorder;
import de.eldoria.companies.components.price.PriceSource;
import de.eldoria.companies.components.price.ShopOrigin;
import de.eldoria.companies.components.price.ShopQuote;
import de.eldoria.companies.components.price.ShopSide;
import de.eldoria.companies.components.price.ShopTrade;
import de.eldoria.companies.prices.PriceHookContext;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Prices of ChestShop: the offers of its shop signs as listings, admin signs as admin prices, and its transactions as
 * trades. ChestShop keeps no list of its shops, so the signs are found where chunks are loaded: those loaded when the
 * source registers, each chunk as it loads, and shops as they are made, edited (ChestShop announces an edit as a
 * shop broken and made again) and broken. A sign stays known while its chunk is unloaded and is forgotten with its
 * world; after a restart only the shops of chunks loaded since count. A sign's price is for its quantity of items, and
 * its item is read as ChestShop reads it, aliases included; a sign naming a stored item by code is left out, as such an
 * item is never plain. Trades of a player with their own shop are left out; members a shop lets in count as anyone.
 */
public final class ChestShopPrices implements PriceSource, Listener {
    private final PriceHookContext context;
    private final Map<ChunkKey, Map<BlockKey, List<ShopQuote>>> signs = new ConcurrentHashMap<>();
    private volatile PriceRecorder recorder = trade -> {};

    /**
     * @param context what the source gets when it is loaded
     */
    public ChestShopPrices(PriceHookContext context) {
        this.context = context;
    }

    @Override
    public String id() {
        return "chestshop";
    }

    @Override
    public String name() {
        return "ChestShop";
    }

    @Override
    public Collection<ShopQuote> quotes() {
        List<ShopQuote> quotes = new ArrayList<>();
        for (var chunk : signs.values()) chunk.values().forEach(quotes::addAll);
        return quotes;
    }

    @Override
    public void record(PriceRecorder recorder) {
        this.recorder = recorder;
        var plugin = context.plugin();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (var world : plugin.getServer().getWorlds()) {
            for (var chunk : world.getLoadedChunks()) scan(chunk);
        }
    }

    /**
     * Reads the shop signs of a chunk that loads.
     *
     * @param event the loading
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        scan(event.getChunk());
    }

    /**
     * Notes a new shop.
     *
     * @param event the shop made
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCreate(ShopCreatedEvent event) {
        put(event.getSign().getLocation(), offers(event.getSignLines()));
    }

    /**
     * Forgets a broken shop.
     *
     * @param event the shop broken
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDestroy(ShopDestroyedEvent event) {
        put(event.getSign().getLocation(), List.of());
    }

    /**
     * Forgets the shops of a world that is unloaded.
     *
     * @param event the unloading
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        var world = event.getWorld().getUID();
        signs.keySet().removeIf(chunk -> chunk.world().equals(world));
    }

    /**
     * Records a transaction with a shop.
     *
     * @param event the transaction
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTransaction(TransactionEvent event) {
        var owner = event.getOwnerAccount();
        trade(
                        event.getTransactionType() == TransactionEvent.TransactionType.BUY,
                        owner == null ? null : owner.getUuid(),
                        event.getClient().getUniqueId(),
                        event.getStock(),
                        event.getExactPrice(),
                        ChestShopSign.isAdminShop(event.getSign()))
                .ifPresent(recorder::trade);
    }

    /**
     * A transaction with a shop as a trade.
     *
     * @param buy    whether the player bought from the shop, rather than sold to it
     * @param owner  the owner of the shop, null when unknown
     * @param client the player
     * @param stock  the stacks that moved, all of one item
     * @param price  what all of them cost
     * @param admin  whether it is an admin shop
     * @return the trade, empty for a trade of a player with their own shop or without items
     */
    static Optional<ShopTrade> trade(
            boolean buy,
            @Nullable UUID owner,
            UUID client,
            ItemStack @Nullable [] stock,
            @Nullable BigDecimal price,
            boolean admin) {
        if (client.equals(owner) || stock == null || price == null) return Optional.empty();
        ItemStack first = null;
        var amount = 0;
        for (var item : stock) {
            if (item == null) continue;
            if (first == null) first = item;
            amount += item.getAmount();
        }
        if (first == null) return Optional.empty();
        var single = first.clone();
        single.setAmount(1);
        return Optional.of(new ShopTrade(
                single,
                amount,
                price.doubleValue(),
                buy ? ShopSide.BUY : ShopSide.SELL,
                admin ? ShopOrigin.ADMIN : ShopOrigin.PLAYER));
    }

    private void scan(Chunk chunk) {
        var key = new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        Map<BlockKey, List<ShopQuote>> found = new ConcurrentHashMap<>();
        try {
            for (var state : chunk.getTileEntities(block -> Tag.ALL_SIGNS.isTagged(block.getType()), false)) {
                if (!(state instanceof Sign sign)) continue;
                var offers = offers(sign.getLines());
                if (!offers.isEmpty()) found.put(BlockKey.of(sign.getLocation()), offers);
            }
        } catch (RuntimeException e) {
            context.plugin().getLogger().log(Level.FINE, "Could not read the ChestShop signs of a chunk", e);
            return;
        }
        if (found.isEmpty()) signs.remove(key);
        else signs.put(key, found);
    }

    private void put(Location location, List<ShopQuote> offers) {
        var block = BlockKey.of(location);
        if (offers.isEmpty()) {
            signs.computeIfPresent(block.chunk(), (key, chunk) -> {
                chunk.remove(block);
                return chunk.isEmpty() ? null : chunk;
            });
        } else {
            signs.computeIfAbsent(block.chunk(), ignored -> new ConcurrentHashMap<>())
                    .put(block, offers);
        }
    }

    private static List<ShopQuote> offers(String[] lines) {
        try {
            if (!ChestShopSign.isValid(lines)) return List.of();
            var itemLine = ChestShopSign.getItem(lines);
            if (itemLine.contains("#")) return List.of();
            var item = ChestShop.callEvent(new ItemParseEvent(itemLine)).getItem();
            var price = ChestShopSign.getPrice(lines);
            return offers(
                    item,
                    ChestShopSign.getQuantity(lines),
                    PriceUtil.getExactBuyPrice(price),
                    PriceUtil.getExactSellPrice(price),
                    ChestShopSign.isAdminShop(lines));
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /**
     * The offers of one shop sign.
     *
     * @param item     the item of the sign, null when it names none ChestShop knows
     * @param quantity how many items a price is for
     * @param buy      what a player pays, {@link PriceUtil#NO_PRICE} when the sign does not sell
     * @param sell     what a player gets, {@link PriceUtil#NO_PRICE} when the sign does not buy
     * @param admin    whether it is an admin shop
     * @return a buy and a sell offer where the sign makes them
     */
    static List<ShopQuote> offers(
            @Nullable ItemStack item,
            int quantity,
            @Nullable BigDecimal buy,
            @Nullable BigDecimal sell,
            boolean admin) {
        if (item == null || quantity < 1) return List.of();
        var origin = admin ? ShopOrigin.ADMIN : ShopOrigin.PLAYER;
        var single = item.clone();
        single.setAmount(1);
        List<ShopQuote> offers = new ArrayList<>();
        if (priced(buy)) offers.add(new ShopQuote(single, quantity, buy.doubleValue(), ShopSide.BUY, origin));
        if (priced(sell)) offers.add(new ShopQuote(single, quantity, sell.doubleValue(), ShopSide.SELL, origin));
        return offers;
    }

    private static boolean priced(@Nullable BigDecimal price) {
        return price != null && price.compareTo(PriceUtil.NO_PRICE) != 0 && price.signum() > 0;
    }

    private record ChunkKey(UUID world, int x, int z) {}

    private record BlockKey(UUID world, int x, int y, int z) {
        static BlockKey of(Location location) {
            return new BlockKey(
                    location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        ChunkKey chunk() {
            return new ChunkKey(world, x >> 4, z >> 4);
        }
    }
}
