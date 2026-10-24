/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.ItemAmount;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import de.eldoria.companies.events.player.ItemsReturnedEvent.ReturnedStack;
import de.eldoria.companies.services.ActionResult;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.util.Inventories;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hands the owner of a delivered order the stacks as they were delivered: what fits goes into the inventory, the rest
 * is stored with the state change and can be collected later with {@code /order collect}. Only the owner receives,
 * and only a delivered order. The checks run on any thread, the inventory is touched on the main thread, where the
 * result completes; the caller tells the player.
 */
public final class OrderReceiveService {
    private final AOrderData orderData;
    private final OrderOutcomes outcomes;
    private final ItemReturnService returns;
    private final Executor mainThread;
    private final Logger logger;

    /**
     * What receiving an order gave its owner.
     *
     * @param order   the received order
     * @param amount  the items of the order
     * @param waiting the items that did not fit and wait to be collected
     */
    public record Received(FullOrder order, int amount, int waiting) {}

    /**
     * @param orderData  order repository
     * @param outcomes   marks orders as received
     * @param returns    stores what does not fit
     * @param mainThread runs tasks on the main thread
     * @param logger     logger of the plugin
     */
    public OrderReceiveService(
            AOrderData orderData,
            OrderOutcomes outcomes,
            ItemReturnService returns,
            Executor mainThread,
            Logger logger) {
        this.orderData = orderData;
        this.outcomes = outcomes;
        this.returns = returns;
        this.mainThread = mainThread;
        this.logger = logger;
    }

    /**
     * Receives a delivered order of a player.
     *
     * @param player owner receiving the order, online on this server
     * @param id     id of the order
     * @return what the player received, or why not
     */
    public CompletableFuture<ActionResult<Received>> receive(Player player, int id) {
        return orderData.retrieveOrderById(id).asFuture().thenCompose(found -> {
            if (found.isEmpty()) return refused(ActionResult.refusedActionBar("error.unknownOrder"));
            var order = found.get();
            if (!order.owner().equals(player.getUniqueId()))
                return refused(ActionResult.refusedChat("error.notYourOrder"));
            if (order.state() != OrderState.DELIVERED) {
                return refused(ActionResult.refusedChat("error.orderNotDelivered"));
            }
            return orderData
                    .retrieveFullOrder(order)
                    .asFuture()
                    .thenComposeAsync(full -> place(player, full), mainThread);
        });
    }

    private CompletableFuture<ActionResult<Received>> place(Player player, FullOrder order) {
        if (!player.isOnline()) return refused(ActionResult.refusedActionBar("dashboard.notOnline"));
        var items = order.deliveredStacks().stream()
                .map(stack -> new ItemAmount(stack, stack.getAmount()))
                .toList();
        var placement = Inventories.place(Inventories.storageCopy(player), items);
        return outcomes.receive(order, placement.remaining())
                .exceptionally(err -> {
                    logger.log(Level.SEVERE, "Could not receive an order", err);
                    return false;
                })
                .thenApplyAsync(received -> handOut(player, order, placement, received), mainThread);
    }

    private ActionResult<Received> handOut(
            Player player, FullOrder order, Inventories.Placement placement, boolean received) {
        if (!received) return ActionResult.refusedChat("error.orderNotDelivered");
        if (!player.isOnline()) {
            keep(player, order, placement.fitting());
            return ActionResult.done(new Received(order, order.amount(), order.amount()));
        }
        var stacks = placement.fitting().stream()
                .flatMap(item -> Inventories.split(item.stack(), item.amount()).stream())
                .toList();
        var leftovers = Inventories.add(player, stacks);
        keep(
                player,
                order,
                leftovers.stream()
                        .map(stack -> new ItemAmount(stack, stack.getAmount()))
                        .toList());
        var waiting = placement.remainingAmount()
                + leftovers.stream().mapToInt(ItemStack::getAmount).sum();
        return ActionResult.done(new Received(order, order.amount(), waiting));
    }

    private void keep(Player player, FullOrder order, List<ItemAmount> items) {
        var stacks = items.stream()
                .filter(item -> item.amount() > 0)
                .map(item -> new ReturnedStack(item.stack(), item.amount()))
                .toList();
        if (stacks.isEmpty()) return;
        returns.storeAll(
                player.getUniqueId(), ReturnKind.OWNER, stacks, ItemsReturnedEvent.Reason.DELIVERY_OVERFLOW, order);
    }

    private static <T> CompletableFuture<ActionResult<T>> refused(ActionResult<T> result) {
        return CompletableFuture.completedFuture(result);
    }
}
