/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.ItemReturn;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Inventories;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.messages.Replacement;
import de.eldoria.eldoutilities.threading.futures.CompletableBukkitFuture;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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
 * Hands stored items to players: items returned to workers after an order they delivered to was released, and items of
 * received orders that did not fit into the owner's inventory.
 * <p>
 * Workers receive their returns when they join or use {@code /company returns}; owners are told on join and collect
 * with {@code /order collect} or the button of the notice. Whatever does not fit stays stored until it is collected later.
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
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> handOut(player, ReturnKind.WORKER, true), 40L);
        orderData
                .retrieveItemReturnCount(player.getUniqueId(), ReturnKind.OWNER)
                .whenComplete(amount -> notifyOwner(player, amount), err -> plugin.getLogger()
                        .log(Level.SEVERE, "Could not count waiting order items", err));
    }

    private void notifyOwner(Player player, int amount) {
        if (amount <= 0 || !player.isOnline()) return;
        var text = MenuText.create()
                .text("<neutral>")
                .locale("order.collect.waiting", Values.number("amount", amount))
                .space()
                .text(
                        "<act_collect><add>[<i18n:words.collect>]</act_collect>",
                        handOutButton("act_collect", player, ReturnKind.OWNER));
        messageSender.sendMessage(player, text);
    }

    /**
     * A chat button handing out the waiting items of a kind. It works once.
     *
     * @param key    placeholder key of the button
     * @param player player the button is shown to
     * @param kind   kind of the items to hand out
     * @return the button
     */
    public TagResolver.Single handOutButton(String key, Player player, ReturnKind kind) {
        return new MenuActions(plugin, player, MenuActions.DEFAULT_LIFETIME)
                .chatActionOnce(key, receiver -> handOut(receiver, kind, false));
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
                var text = MenuText.create()
                        .text("<neutral><i18n:company.returns.waiting> ")
                        .text(
                                "<act_receive><add>[<i18n:words.receive>]</act_receive>",
                                handOutButton("act_receive", player, ReturnKind.WORKER));
                messageSender.sendMessage(player, text);
            }
        });
    }

    /**
     * Gives the player as many stored items of a kind as fit into the storage. Has to be called on the main thread.
     *
     * @param player player to receive the items
     * @param kind   kind of the items to hand out
     * @param silent true to send no message when nothing is waiting
     */
    public void handOut(Player player, ReturnKind kind, boolean silent) {
        var messages = Messages.of(kind);
        orderData
                .retrieveItemReturns(player.getUniqueId(), kind)
                .whenComplete(
                        returns -> {
                            if (returns.isEmpty()) {
                                if (!silent) messageSender.sendMessage(player, messages.none());
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
                                        player, messages.remaining(), Replacement.create("amount", total));
                                return;
                            }
                            take(player, kind, takes, total);
                        },
                        err -> plugin.getLogger().log(Level.SEVERE, "Could not load item returns", err));
    }

    private void take(Player player, ReturnKind kind, Map<ItemReturn, Integer> takes, int total) {
        CompletableBukkitFuture.supplyAsync(
                        () -> takes.entrySet().stream()
                                .filter(entry -> Boolean.TRUE.equals(orderData
                                        .submitItemReturnTake(entry.getKey(), entry.getValue())
                                        .join()))
                                .toList(),
                        VIRTUAL)
                .whenComplete(taken -> give(player, kind, taken, total), err -> plugin.getLogger()
                        .log(Level.SEVERE, "Could not take item returns", err));
    }

    private void give(Player player, ReturnKind kind, List<Map.Entry<ItemReturn, Integer>> taken, int total) {
        if (!player.isOnline()) {
            for (var entry : taken) {
                orderData.submitItemReturn(
                        player.getUniqueId(), kind, entry.getKey().stack(), entry.getValue());
            }
            return;
        }
        var messages = Messages.of(kind);
        var received = 0;
        for (var entry : taken) {
            Inventories.give(player, Inventories.split(entry.getKey().stack(), entry.getValue()));
            received += entry.getValue();
        }
        if (received > 0) {
            messageSender.sendMessage(player, messages.received(), Replacement.create("amount", received));
        }
        var remaining = total - received;
        if (remaining > 0) {
            messageSender.sendMessage(player, messages.remaining(), Replacement.create("amount", remaining));
        }
    }

    private record Messages(String none, String received, String remaining) {
        private static Messages of(ReturnKind kind) {
            return switch (kind) {
                case WORKER ->
                    new Messages("company.returns.none", "company.returns.received", "company.returns.remaining");
                case OWNER -> new Messages("order.collect.none", "order.collect.received", "order.collect.remaining");
            };
        }
    }
}
