/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.OwnDeliveredOrderIds;
import de.eldoria.companies.commands.suggest.SuggestionSources;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.ItemAmount;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import de.eldoria.companies.events.player.ItemsReturnedEvent.ReturnedStack;
import de.eldoria.companies.menus.MenuService;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Inventories;
import de.eldoria.companies.util.Threading;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.logging.Level;

/**
 * {@code /order receive <id>}: hands the owner of a delivered order the stacks as they were delivered.
 */
@Subcommand("receive")
@PlayerOnly
public final class Receive {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public Receive(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * @param sender sender of the command
     * @param id     id of the order
     */
    @Executes
    public void receive(CommandSender sender, @OwnDeliveredOrderIds int id) {
        var player = Senders.player(sender);
        var menus = deps.services().menus();
        var orderData = deps.orderData();

        orderData.retrieveOrderById(id).whenComplete(optOrder -> {
            if (optOrder.isEmpty()) {
                deps.messages().sendErrorActionBar(player, "error.unknownOrder");
                return;
            }
            var simpleOrder = optOrder.get();
            if (!simpleOrder.owner().equals(player.getUniqueId())) {
                deps.messages().sendError(player, "error.notYourOrder");
                return;
            }
            if (simpleOrder.state() != OrderState.DELIVERED) {
                deps.messages().sendError(player, "error.orderNotDelivered");
                return;
            }
            orderData.retrieveFullOrder(simpleOrder).whenComplete(fullOrder -> receive(player, fullOrder, menus));
        });
    }

    /**
     * Receives a delivered order. What fits goes into the inventory; the rest is stored with the state change, so it
     * can be collected later with {@code /order collect}.
     *
     * @param player owner receiving the order
     * @param order  delivered order
     * @param menus  menus to close once the order is received
     */
    private void receive(Player player, FullOrder order, MenuService menus) {
        if (!player.isOnline()) return;
        var items = order.deliveredStacks().stream()
                .map(stack -> new ItemAmount(stack, stack.getAmount()))
                .toList();
        var placement = Inventories.place(Inventories.storageCopy(player), items);
        deps.outcomes()
                .receive(order, placement.remaining())
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return false;
                })
                .thenAcceptAsync(
                        received -> handOut(player, order, placement, received, menus),
                        Threading.mainThread(deps.plugin()));
    }

    /**
     * Gives the owner what fits once the order is received. Has to be called on the main thread.
     *
     * @param player    owner receiving the order
     * @param order     the order
     * @param placement what fits into the inventory and what is stored
     * @param received  whether the order was received
     * @param menus     menus to close once the order is received
     */
    private void handOut(
            Player player, FullOrder order, Inventories.Placement placement, boolean received, MenuService menus) {
        if (!received) {
            deps.messages().sendError(player, "error.orderNotDelivered");
            return;
        }
        SuggestionSources.current().ifPresent(sources -> sources.ownOrders().invalidate(player.getUniqueId()));
        if (!player.isOnline()) {
            keep(player, order, placement.fitting());
            return;
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
        menus.close(player).thenRun(() -> sendReceived(player, order.amount(), waiting));
    }

    private void keep(Player player, FullOrder order, List<ItemAmount> items) {
        var stacks = items.stream()
                .filter(item -> item.amount() > 0)
                .map(item -> new ReturnedStack(item.stack(), item.amount()))
                .toList();
        deps.services()
                .itemReturnService()
                .storeAll(
                        player.getUniqueId(),
                        ReturnKind.OWNER,
                        stacks,
                        ItemsReturnedEvent.Reason.DELIVERY_OVERFLOW,
                        order);
    }

    private void sendReceived(Player player, int total, int waiting) {
        if (waiting <= 0) {
            deps.messages().sendMessage(player, "order.receive.received");
            return;
        }
        var text = MenuText.create()
                .locale(
                        "order.receive.receivedPartly",
                        Values.number("received", total - waiting),
                        Values.number("waiting", waiting))
                .space()
                .text(
                        "<act_collect><add>[<i18n:words.collect>]</act_collect>",
                        deps.services().itemReturnService().handOutButton("act_collect", player, ReturnKind.OWNER));
        deps.messages().sendMessage(player, text);
    }
}
