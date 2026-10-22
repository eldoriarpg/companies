/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.ItemReturn;
import de.eldoria.companies.util.Inventories;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.messages.Replacement;
import de.eldoria.eldoutilities.threading.futures.CompletableBukkitFuture;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import static de.eldoria.companies.util.Threading.VIRTUAL;

/**
 * Hands items back to workers, which they delivered to orders that were released before completion.
 * <p>
 * Workers receive what fits into their storage when they join or use {@code /company returns}. Everything else stays stored until they
 * collect it later.
 */
public class ItemReturnService implements Listener {
    private final Plugin plugin;
    private final AOrderData orderData;
    private final MessageSender messageSender;

    public ItemReturnService(Plugin plugin, AOrderData orderData) {
        this.plugin = plugin;
        this.orderData = orderData;
        messageSender = MessageSender.getPluginMessageSender(plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> handOut(player, true), 40L);
    }

    /**
     * Tells online workers that items are waiting for them.
     *
     * @param workers workers who got items returned
     */
    public void notifyWorkers(Collection<UUID> workers) {
        if (workers.isEmpty()) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (var worker : workers) {
                var player = plugin.getServer().getPlayer(worker);
                if (player == null) continue;
                var composer = MessageComposer.create()
                        .text("<neutral>")
                        .localeCode("company.returns.waiting")
                        .space()
                        .text("<click:run_command:/company returns><add>[")
                        .localeCode("words.receive")
                        .text("]</click>");
                messageSender.sendMessage(player, composer);
            }
        });
    }

    /**
     * Gives the player as many returned items as fit into the storage. Has to be called on the main thread.
     *
     * @param player player to receive the items
     * @param silent true to send no message when nothing is waiting
     */
    public void handOut(Player player, boolean silent) {
        orderData
                .retrieveItemReturns(player.getUniqueId())
                .whenComplete(
                        returns -> {
                            if (returns.isEmpty()) {
                                if (!silent) messageSender.sendMessage(player, "company.returns.none");
                                return;
                            }
                            if (!player.isOnline()) return;
                            var storage = Inventories.storageCopy(player);
                            Map<ItemReturn, Integer> takes = new LinkedHashMap<>();
                            for (var itemReturn : returns) {
                                var fit = Inventories.fit(storage, itemReturn.stack(), itemReturn.amount());
                                if (fit > 0) takes.put(itemReturn, fit);
                            }
                            var total = returns.stream()
                                    .mapToInt(ItemReturn::amount)
                                    .sum();
                            if (takes.isEmpty()) {
                                messageSender.sendMessage(
                                        player, "company.returns.remaining", Replacement.create("amount", total));
                                return;
                            }
                            take(player, takes, total);
                        },
                        err -> plugin.getLogger().log(Level.SEVERE, "Could not load item returns", err));
    }

    private void take(Player player, Map<ItemReturn, Integer> takes, int total) {
        CompletableBukkitFuture.supplyAsync(
                        () -> takes.entrySet().stream()
                                .filter(entry -> Boolean.TRUE.equals(orderData
                                        .submitItemReturnTake(entry.getKey(), entry.getValue())
                                        .join()))
                                .toList(),
                        VIRTUAL)
                .whenComplete(taken -> give(player, taken, total), err -> plugin.getLogger()
                        .log(Level.SEVERE, "Could not take item returns", err));
    }

    private void give(Player player, List<Map.Entry<ItemReturn, Integer>> taken, int total) {
        if (!player.isOnline()) {
            for (var entry : taken) {
                orderData.submitItemReturn(player.getUniqueId(), entry.getKey().stack(), entry.getValue());
            }
            return;
        }
        var received = 0;
        for (var entry : taken) {
            Inventories.give(player, Inventories.split(entry.getKey().stack(), entry.getValue()));
            received += entry.getValue();
        }
        if (received > 0) {
            messageSender.sendMessage(player, "company.returns.received", Replacement.create("amount", received));
        }
        var remaining = total - received;
        if (remaining > 0) {
            messageSender.sendMessage(player, "company.returns.remaining", Replacement.create("amount", remaining));
        }
    }
}
