/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The inventory an owner hands the materials of a supplied build job in with. Its top rows show what the stock still
 * lacks, each item with the count missing, the last of them how many more kinds are missing when they do not all fit;
 * the rows below take the items. Closing it puts what is missing into the stock and gives everything else back, with
 * an empty bucket for each full one taken; what does not fit into the player's inventory drops where they stand. A
 * player who left meanwhile, or a server that stops, puts what goes back into their item returns. The rows that show
 * the needs can not be changed.
 */
public final class HandIns implements Listener {
    /**
     * The slots that show what is missing.
     */
    public static final int SHOWN = 27;

    private static final int SIZE = 54;

    private final Plugin plugin;
    private final Supplier<Supplies> supplies;
    private final Supplier<ItemReturnService> returns;
    private final MessageSender messages;
    private final Handed handed;

    /**
     * Tells a player how a hand-in went, on the main thread.
     */
    @FunctionalInterface
    public interface Handed {
        /**
         * @param player  the player
         * @param job     id of the job
         * @param missing what the stock still lacks, or null when the job took nothing, as it is no supplied draft any
         *                more or the items could not be stored
         */
        void handed(Player player, int job, @Nullable Map<Material, Integer> missing);
    }

    /**
     * @param plugin   the plugin
     * @param supplies the supplies of build jobs, asked when a hand-in opens or closes
     * @param returns  keeps what can not be given back in game
     * @param messages renders the names and lore
     * @param handed   tells the player how the hand-in went
     */
    public HandIns(
            Plugin plugin,
            Supplier<Supplies> supplies,
            Supplier<ItemReturnService> returns,
            MessageSender messages,
            Handed handed) {
        this.plugin = plugin;
        this.supplies = supplies;
        this.returns = returns;
        this.messages = messages;
        this.handed = handed;
    }

    /**
     * The inventory of one hand-in.
     */
    private static final class HandIn implements InventoryHolder {
        private final int job;
        private Inventory inventory;

        private HandIn(int job) {
            this.job = job;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    /**
     * Opens the hand-in of a job for a player. Reads the stock off the main thread and opens on it.
     *
     * @param player the owner of the job
     * @param job    id of a supplied build job
     */
    public void open(Player player, int job) {
        CompletableFuture.supplyAsync(() -> supplies.get().missing(job), Threading.VIRTUAL)
                .thenAcceptAsync(missing -> show(player, job, missing), Threading.mainThread(plugin))
                .exceptionally(err -> {
                    plugin.getLogger().log(Level.SEVERE, "Could not open the hand-in of job " + job, err);
                    return null;
                });
    }

    private void show(Player player, int job, Map<Material, Integer> missing) {
        if (!player.isOnline()) return;
        var components = new MenuComponents(messages, player);
        var holder = new HandIn(job);
        var inventory = plugin.getServer()
                .createInventory(holder, SIZE, components.locale("order.job.handIn.title", Values.number("id", job)));
        holder.inventory = inventory;
        var entries = List.copyOf(missing.entrySet());
        var fits = entries.size() <= SHOWN ? entries.size() : SHOWN - 1;
        var slot = 0;
        for (var entry : entries.subList(0, fits)) {
            var shown = new ItemStack(entry.getKey().isItem() ? entry.getKey() : Material.BARRIER);
            var amount = entry.getValue();
            shown.editMeta(meta ->
                    meta.lore(List.of(components.locale("order.job.handIn.missing", Values.number("amount", amount)))));
            inventory.setItem(slot++, shown);
        }
        if (fits < entries.size()) {
            var more = new ItemStack(Material.PAPER);
            var kinds = entries.size() - fits;
            more.editMeta(meta ->
                    meta.displayName(components.locale("order.job.handIn.more", Values.number("types", kinds))));
            inventory.setItem(slot++, more);
        }
        while (slot < SHOWN) {
            var pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            pane.editMeta(meta -> meta.displayName(Component.empty()));
            inventory.setItem(slot++, pane);
        }
        player.openInventory(inventory);
    }

    /**
     * Keeps the rows that show the needs as they are, and moves shift-clicked items into the rows that take them.
     *
     * @param event the click
     */
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof HandIn)) return;
        var raw = event.getRawSlot();
        if (raw >= 0 && raw < SHOWN) {
            event.setCancelled(true);
            return;
        }
        if (event.isShiftClick() && raw >= SIZE) {
            event.setCancelled(true);
            var item = event.getCurrentItem();
            if (item == null || item.getType().isAir()) return;
            event.setCurrentItem(moveIn(event.getView().getTopInventory(), item.clone()));
        }
    }

    /**
     * Puts an item into the rows of a hand-in that take items, onto stacks of the same item first.
     *
     * @param top  the hand-in
     * @param item the item
     * @return what did not fit, or null
     */
    static @Nullable ItemStack moveIn(Inventory top, ItemStack item) {
        for (var slot = SHOWN; slot < SIZE && item.getAmount() > 0; slot++) {
            var there = top.getItem(slot);
            if (there == null || !there.isSimilar(item)) continue;
            var add = Math.min(item.getAmount(), there.getMaxStackSize() - there.getAmount());
            if (add <= 0) continue;
            there.setAmount(there.getAmount() + add);
            top.setItem(slot, there);
            item.setAmount(item.getAmount() - add);
        }
        for (var slot = SHOWN; slot < SIZE && item.getAmount() > 0; slot++) {
            var there = top.getItem(slot);
            if (there != null && !there.getType().isAir()) continue;
            var add = Math.min(item.getAmount(), item.getMaxStackSize());
            top.setItem(slot, item.asQuantity(add));
            item.setAmount(item.getAmount() - add);
        }
        return item.getAmount() > 0 ? item : null;
    }

    /**
     * Keeps items from being dragged into the rows that show the needs.
     *
     * @param event the drag
     */
    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof HandIn)) return;
        if (event.getRawSlots().stream().anyMatch(raw -> raw < SHOWN)) event.setCancelled(true);
    }

    /**
     * Takes the handed-in items into the stock and gives the rest back.
     *
     * @param event the closing
     */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof HandIn handIn)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        List<ItemStack> items = new ArrayList<>();
        var inventory = event.getInventory();
        for (var slot = SHOWN; slot < SIZE; slot++) {
            var item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) items.add(item.clone());
            inventory.setItem(slot, null);
        }
        if (!items.isEmpty()) close(player, handIn.job, items);
    }

    /**
     * Hands items in off the main thread, then gives back what the job did not take and tells the player. Items go back
     * only as {@link Supplies#handIn} says, so what reached the stock is never given back too.
     *
     * @param player the player
     * @param job    id of the job
     * @param items  the items handed in
     * @return completes once the items are given back
     */
    CompletableFuture<Void> close(Player player, int job, List<ItemStack> items) {
        return CompletableFuture.supplyAsync(() -> handIn(job, items), Threading.VIRTUAL)
                .thenAccept(result -> giveBack(player, job, result));
    }

    /**
     * What came of a hand-in.
     *
     * @param handed  what was taken and what goes back
     * @param missing what the stock still lacks, null when the job took nothing
     * @param tell    whether the player is told; false when what is missing could not be read after storing
     */
    private record Result(Supplies.Handed handed, @Nullable Map<Material, Integer> missing, boolean tell) {}

    private Result handIn(int job, List<ItemStack> items) {
        var known = supplies.get();
        Supplies.Handed done;
        try {
            done = known.handIn(job, items);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not hand in the materials of job " + job, e);
            done = new Supplies.Handed(false, Map.of(), List.copyOf(items));
        }
        if (!done.stored()) return new Result(done, null, true);
        try {
            return new Result(done, known.missing(job), true);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read what job " + job + " still lacks", e);
            return new Result(done, Map.of(), false);
        }
    }

    private void giveBack(Player player, int job, Result result) {
        var back = result.handed().back();
        try {
            Threading.mainThread(plugin).execute(() -> {
                if (!player.isOnline()) {
                    keep(player, back);
                    return;
                }
                for (var item : back) {
                    player.getInventory().addItem(item).values().forEach(left -> player.getWorld()
                            .dropItemNaturally(player.getLocation(), left));
                }
                if (result.tell()) handed.handed(player, job, result.missing());
            });
        } catch (IllegalPluginAccessException e) {
            keep(player, back);
        }
    }

    private void keep(Player player, List<ItemStack> items) {
        List<ItemsReturnedEvent.ReturnedStack> stacks = new ArrayList<>();
        for (var item : items) {
            if (item.getAmount() > 0) stacks.add(new ItemsReturnedEvent.ReturnedStack(item, item.getAmount()));
        }
        returns.get()
                .storeAll(player.getUniqueId(), ReturnKind.OWNER, stacks, ItemsReturnedEvent.Reason.JOB_HAND_IN, null)
                .exceptionally(err -> {
                    plugin.getLogger().log(Level.SEVERE, "Could not keep the hand-in of " + player.getName(), err);
                    return false;
                });
    }
}
