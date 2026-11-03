/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.util.Threading;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Hands the materials of a supplied draft in from a player: items they put into the hand-in, or what their inventory
 * holds of what the draft still lacks. The stock takes them as {@link Supplies#handIn} says, off the main thread; what
 * it did not take, and an empty bucket for each full one taken, goes back into the inventory on the main thread, what
 * does not fit drops where the player stands, and a player who left meanwhile, or a server that stops, gets it in their
 * item returns. Taking from the inventory reads only the main inventory and hotbar, plain items only, and never more of
 * a type than the draft lacks.
 */
public final class InventoryHandIn {
    private static final int STORAGE_SLOTS = 36;

    private final Plugin plugin;
    private final Supplier<Supplies> supplies;
    private final Executor mainThread;
    private volatile Supplier<@Nullable ItemReturnService> returns = () -> null;

    /**
     * @param plugin   the plugin
     * @param supplies the supplies of build jobs
     */
    public InventoryHandIn(Plugin plugin, Supplier<Supplies> supplies) {
        this(plugin, supplies, Threading.mainThread(plugin));
    }

    /**
     * @param plugin     the plugin
     * @param supplies   the supplies of build jobs
     * @param mainThread runs tasks on the main thread
     */
    public InventoryHandIn(Plugin plugin, Supplier<Supplies> supplies, Executor mainThread) {
        this.plugin = plugin;
        this.supplies = supplies;
        this.mainThread = mainThread;
    }

    /**
     * Sets where items go that can not be given back in game.
     *
     * @param returns the item returns
     */
    public void returns(Supplier<@Nullable ItemReturnService> returns) {
        this.returns = returns;
    }

    /**
     * What came of a hand-in.
     *
     * @param online  whether the player was online to hand in
     * @param stored  whether the draft took items; false when it is no supplied draft any more or they could not be
     *                stored, and everything went back
     * @param taken   what went into the stock, by type
     * @param missing what the stock still lacks, null when it took nothing or could not be read after storing
     */
    public record Handed(
            boolean online,
            boolean stored,
            Map<Material, Integer> taken,
            @Nullable Map<Material, Integer> missing) {
        static final Handed OFFLINE = new Handed(false, false, Map.of(), null);
    }

    /**
     * Takes what a draft still lacks out of a player's inventory into its stock.
     *
     * @param player the owner of the draft
     * @param job    id of the draft
     * @return what came of it; {@link Handed#online()} false when the player is not online on this server
     */
    public CompletableFuture<Handed> take(UUID player, int job) {
        return take(player, job, () -> true);
    }

    /**
     * Takes what a draft still lacks out of a player's inventory into its stock, unless the caller gave up before the
     * inventory is read.
     *
     * @param player the owner of the draft
     * @param job    id of the draft
     * @param wanted whether the caller still waits for it, asked on the main thread before anything is taken
     * @return what came of it; {@link Handed#online()} false when the player is not online on this server
     */
    public CompletableFuture<Handed> take(UUID player, int job, BooleanSupplier wanted) {
        return CompletableFuture.supplyAsync(() -> supplies.get().missing(job), Threading.VIRTUAL)
                .thenComposeAsync(
                        missing -> {
                            if (!wanted.getAsBoolean()) return CompletableFuture.completedFuture(Handed.OFFLINE);
                            var online = plugin.getServer().getPlayer(player);
                            if (online == null || !online.isOnline())
                                return CompletableFuture.completedFuture(Handed.OFFLINE);
                            var items = takeFrom(online.getInventory(), missing);
                            if (items.isEmpty())
                                return CompletableFuture.completedFuture(new Handed(true, true, Map.of(), missing));
                            return handIn(online, job, items);
                        },
                        mainThread);
    }

    /**
     * @param inventory a player's inventory, on the main thread
     * @param missing   what a draft still lacks, by type
     * @return the plain items of the main inventory and hotbar the draft lacks, taken out of it, at most what is
     *     missing of each type
     */
    static List<ItemStack> takeFrom(PlayerInventory inventory, Map<Material, Integer> missing) {
        Map<Material, Integer> left = new LinkedHashMap<>(missing);
        List<ItemStack> taken = new ArrayList<>();
        for (var slot = 0; slot < STORAGE_SLOTS; slot++) {
            var item = inventory.getItem(slot);
            if (item == null || item.getType().isAir() || !item.isSimilar(new ItemStack(item.getType()))) continue;
            var wanted = left.getOrDefault(item.getType(), 0);
            if (wanted <= 0) continue;
            var take = Math.min(wanted, item.getAmount());
            left.put(item.getType(), wanted - take);
            taken.add(item.asQuantity(take));
            if (take == item.getAmount()) {
                inventory.setItem(slot, null);
            } else {
                item.setAmount(item.getAmount() - take);
                inventory.setItem(slot, item);
            }
        }
        return taken;
    }

    /**
     * Hands items in off the main thread, then gives back what the draft did not take on the main thread. Items go
     * back only as {@link Supplies#handIn} says, so what reached the stock is never given back too.
     *
     * @param player the player
     * @param job    id of the draft
     * @param items  the items handed in, out of the player's inventory already
     * @return what came of it, once the items are given back
     */
    public CompletableFuture<Handed> handIn(Player player, int job, List<ItemStack> items) {
        var result = new CompletableFuture<Handed>();
        CompletableFuture.supplyAsync(() -> store(job, items), Threading.VIRTUAL)
                .thenAccept(stored -> {
                    try {
                        mainThread.execute(() -> {
                            if (player.isOnline()) {
                                give(player, stored.back());
                            } else {
                                keep(player.getUniqueId(), stored.back());
                            }
                            result.complete(stored.handed());
                        });
                    } catch (IllegalPluginAccessException e) {
                        keep(player.getUniqueId(), stored.back());
                        result.complete(stored.handed());
                    }
                    // TODO: keep what goes back in the item returns when the plugin stops after the task was scheduled
                });
        return result;
    }

    /**
     * A hand-in stored, and what goes back.
     *
     * @param handed what came of it
     * @param back   what goes back to the player
     */
    private record Stored(Handed handed, List<ItemStack> back) {}

    private Stored store(int job, List<ItemStack> items) {
        var known = supplies.get();
        Supplies.Handed done;
        try {
            done = known.handIn(job, items);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not hand in the materials of job " + job, e);
            done = new Supplies.Handed(false, Map.of(), List.copyOf(items));
        }
        if (!done.stored()) return new Stored(new Handed(true, false, Map.of(), null), done.back());
        Map<Material, Integer> missing;
        try {
            missing = known.missing(job);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read what job " + job + " still lacks", e);
            missing = null;
        }
        return new Stored(new Handed(true, true, done.taken(), missing), done.back());
    }

    private static void give(Player player, List<ItemStack> items) {
        for (var item : items) {
            player.getInventory().addItem(item).values().forEach(left -> player.getWorld()
                    .dropItemNaturally(player.getLocation(), left));
        }
    }

    private void keep(UUID player, List<ItemStack> items) {
        if (items.isEmpty()) return;
        var service = returns.get();
        if (service == null) {
            plugin.getLogger().severe("No item returns to keep " + items + " of " + player + " in");
            return;
        }
        List<ItemsReturnedEvent.ReturnedStack> stacks = new ArrayList<>();
        for (var item : items) {
            if (item.getAmount() > 0) stacks.add(new ItemsReturnedEvent.ReturnedStack(item, item.getAmount()));
        }
        service.storeAll(player, ReturnKind.OWNER, stacks, ItemsReturnedEvent.Reason.JOB_HAND_IN, null)
                .exceptionally(err -> {
                    plugin.getLogger().log(Level.SEVERE, "Could not keep the hand-in of " + player, err);
                    return false;
                });
    }
}
