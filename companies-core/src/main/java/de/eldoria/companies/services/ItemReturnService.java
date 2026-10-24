/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.ItemReturn;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.data.wrapper.order.ReturnedItems;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import de.eldoria.companies.events.player.ItemsReturnedEvent.ReturnedStack;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Inventories;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.messages.Replacement;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import static de.eldoria.companies.util.Threading.VIRTUAL;

/**
 * Hands stored items to players: items returned to workers after an order they delivered to was released, and items of
 * received orders that did not fit into the owner's inventory.
 * <p>
 * Workers receive their returns when they join or use {@code /company returns}; owners are told on join and collect
 * with {@code /order collect} or the button of the notice. Whatever does not fit stays stored until it is collected later.
 * <p>
 * This is the one place that fires {@link ItemsReturnedEvent}: items are stored with {@link #store}, and items the
 * repository already moved inside its own transaction are reported with {@link #returned}. Items taken out and put back
 * because their player went offline are not returned again and fire nothing.
 */
public class ItemReturnService implements Listener {
    private final Plugin plugin;
    private final AOrderData orderData;

    /**
     * @param plugin    the plugin, which fires the events
     * @param orderData order repository
     */
    public ItemReturnService(Plugin plugin, AOrderData orderData) {
        this.plugin = plugin;
        this.orderData = orderData;
    }

    /**
     * Stores items for a player and fires {@link ItemsReturnedEvent} once they are stored.
     *
     * @param player player owning the items
     * @param kind   kind of the items
     * @param stack  item to store
     * @param amount amount of items
     * @param reason why the items are returned
     * @param order  order the items came from, or null
     * @return true when the items were stored
     */
    public CompletableFuture<Boolean> store(
            UUID player,
            ReturnKind kind,
            ItemStack stack,
            int amount,
            ItemsReturnedEvent.Reason reason,
            @Nullable ISimpleOrder order) {
        return storeAll(player, kind, List.of(new ReturnedStack(stack, amount)), reason, order);
    }

    /**
     * Stores several items for a player and fires one {@link ItemsReturnedEvent} for those that were stored.
     *
     * @param player player owning the items
     * @param kind   kind of the items
     * @param stacks items to store
     * @param reason why the items are returned
     * @param order  order the items came from, or null
     * @return true when every item was stored
     */
    public CompletableFuture<Boolean> storeAll(
            UUID player,
            ReturnKind kind,
            List<ReturnedStack> stacks,
            ItemsReturnedEvent.Reason reason,
            @Nullable ISimpleOrder order) {
        if (stacks.isEmpty()) return CompletableFuture.completedFuture(true);
        return CompletableFuture.supplyAsync(
                () -> {
                    List<ReturnedStack> stored = new ArrayList<>();
                    for (var stack : stacks) {
                        var saved = orderData
                                .submitItemReturn(player, kind, stack.stack(), stack.amount())
                                .asFuture()
                                .join();
                        if (Boolean.TRUE.equals(saved)) stored.add(stack);
                    }
                    returned(player, stored, reason, order);
                    return stored.size() == stacks.size();
                },
                VIRTUAL);
    }

    /**
     * Reports items the repository already moved to the item returns of an order's workers, and tells the online
     * workers. Fires one {@link ItemsReturnedEvent} per worker.
     *
     * @param order    order the items came from
     * @param returned the returned items by worker
     * @param reason   why the items were returned
     */
    public void returned(ISimpleOrder order, ReturnedItems returned, ItemsReturnedEvent.Reason reason) {
        returned.byWorker().forEach((worker, stacks) -> returned(worker, stacks, reason, order));
        notifyWorkers(returned.workers());
    }

    /**
     * Reports items already stored for a player. Fires {@link ItemsReturnedEvent} unless there are no items.
     *
     * @param player player owning the items
     * @param stacks the stored items
     * @param reason why the items were returned
     * @param order  order the items came from, or null
     */
    public void returned(
            UUID player, List<ReturnedStack> stacks, ItemsReturnedEvent.Reason reason, @Nullable ISimpleOrder order) {
        if (stacks.isEmpty()) return;
        var server = plugin.getServer();
        server.getPluginManager()
                .callEvent(new ItemsReturnedEvent(server.getOfflinePlayer(player), stacks, reason, order));
    }

    private MessageSender messageSender() {
        return MessageSender.getPluginMessageSender(plugin);
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
        messageSender().sendMessage(player, text);
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
                messageSender().sendMessage(player, text);
            }
        });
    }

    /**
     * What handing out stored items gave a player.
     *
     * @param received  items put into the inventory
     * @param remaining items still stored, because they did not fit or the player left
     */
    public record Collected(int received, int remaining) {}

    /**
     * Gives the player as many stored items of a kind as fit into the storage and tells the player how it went. Has to
     * be called on the main thread.
     *
     * @param player player to receive the items
     * @param kind   kind of the items to hand out
     * @param silent true to send no message when nothing is waiting
     */
    public void handOut(Player player, ReturnKind kind, boolean silent) {
        var messages = Messages.of(kind);
        collect(player, kind)
                .thenAcceptAsync(collected -> report(player, messages, collected, silent), Threading.mainThread(plugin))
                .exceptionally(err -> {
                    plugin.getLogger().log(Level.SEVERE, "Could not hand out item returns", err);
                    return null;
                });
    }

    /**
     * Gives the player as many stored items of a kind as fit into the storage, without a message. Has to be called on
     * the main thread; the result completes on it.
     *
     * @param player player to receive the items
     * @param kind   kind of the items to hand out
     * @return what the player received and what is still stored
     */
    public CompletableFuture<Collected> collect(Player player, ReturnKind kind) {
        var main = Threading.mainThread(plugin);
        return orderData
                .retrieveItemReturns(player.getUniqueId(), kind)
                .asFuture()
                .thenComposeAsync(
                        returns -> {
                            var total = returns.stream()
                                    .mapToInt(ItemReturn::amount)
                                    .sum();
                            if (returns.isEmpty() || !player.isOnline()) {
                                return CompletableFuture.completedFuture(new Collected(0, total));
                            }
                            var storage = Inventories.storageCopy(player);
                            Map<ItemReturn, Integer> takes = new LinkedHashMap<>();
                            for (var itemReturn : returns) {
                                var fit = Inventories.fit(storage, itemReturn.stack(), itemReturn.amount());
                                if (fit > 0) takes.put(itemReturn, fit);
                            }
                            if (takes.isEmpty()) return CompletableFuture.completedFuture(new Collected(0, total));
                            return CompletableFuture.supplyAsync(() -> take(takes), VIRTUAL)
                                    .thenApplyAsync(taken -> give(player, kind, taken, total), main);
                        },
                        main);
    }

    private List<Map.Entry<ItemReturn, Integer>> take(Map<ItemReturn, Integer> takes) {
        return takes.entrySet().stream()
                .filter(entry -> Boolean.TRUE.equals(orderData
                        .submitItemReturnTake(entry.getKey(), entry.getValue())
                        .join()))
                .toList();
    }

    private Collected give(Player player, ReturnKind kind, List<Map.Entry<ItemReturn, Integer>> taken, int total) {
        if (!player.isOnline()) {
            for (var entry : taken) {
                orderData.submitItemReturn(
                        player.getUniqueId(), kind, entry.getKey().stack(), entry.getValue());
            }
            return new Collected(0, total);
        }
        var received = 0;
        for (var entry : taken) {
            Inventories.give(player, Inventories.split(entry.getKey().stack(), entry.getValue()));
            received += entry.getValue();
        }
        return new Collected(received, total - received);
    }

    private void report(Player player, Messages messages, Collected collected, boolean silent) {
        if (!player.isOnline()) return;
        if (collected.received() == 0 && collected.remaining() == 0) {
            if (!silent) messageSender().sendMessage(player, messages.none());
            return;
        }
        if (collected.received() > 0) {
            messageSender()
                    .sendMessage(player, messages.received(), Replacement.create("amount", collected.received()));
        }
        if (collected.remaining() > 0) {
            messageSender()
                    .sendMessage(player, messages.remaining(), Replacement.create("amount", collected.remaining()));
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
