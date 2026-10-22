/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.ItemAmount;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.util.Inventories;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.Replacement;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Receive extends AdvancedCommand implements IPlayerTabExecutor {
    private final AOrderData orderData;
    private final MessageBlocker messageBlocker;

    public Receive(Plugin plugin, AOrderData orderData, MessageBlocker messageBlocker) {
        super(plugin, CommandMeta.builder("receive").addArgument("id", true).build());
        this.orderData = orderData;
        this.messageBlocker = messageBlocker;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments)
            throws CommandException {
        var id = arguments.asInt(0);

        orderData.retrieveOrderById(id).whenComplete(optOrder -> {
            if (optOrder.isEmpty()) {
                messageSender().sendErrorActionBar(player, "error.unknownOrder");
                return;
            }

            var simpleOrder = optOrder.get();
            if (!simpleOrder.owner().equals(player.getUniqueId())) {
                messageSender().sendError(player, "error.notYourOrder");
                return;
            }
            if (simpleOrder.state() != OrderState.DELIVERED) {
                messageSender().sendError(player, "error.orderNotDelivered");
                return;
            }
            orderData.retrieveFullOrder(optOrder.get()).whenComplete(fullOrder -> receive(player, fullOrder));
        });
    }

    /**
     * Receives a delivered order. What fits goes into the inventory; the rest is stored with the state change, so it
     * can be collected later with {@code /order collect}.
     *
     * @param player owner receiving the order
     * @param order  delivered order
     */
    private void receive(Player player, FullOrder order) {
        if (!player.isOnline()) return;
        var items = order.contents().stream()
                .map(content -> new ItemAmount(content.stack(), content.amount()))
                .toList();
        var placement = Inventories.place(Inventories.storageCopy(player), items);
        orderData.submitOrderReceive(order, placement.remaining()).whenComplete(received -> {
            if (!received) {
                messageSender().sendError(player, "error.orderNotDelivered");
                return;
            }
            if (!player.isOnline()) {
                keep(player, placement.fitting());
                return;
            }
            var stacks = placement.fitting().stream()
                    .flatMap(item -> Inventories.split(item.stack(), item.amount()).stream())
                    .toList();
            var leftovers = Inventories.add(player, stacks);
            keep(
                    player,
                    leftovers.stream()
                            .map(stack -> new ItemAmount(stack, stack.getAmount()))
                            .toList());
            var waiting = placement.remainingAmount()
                    + leftovers.stream().mapToInt(ItemStack::getAmount).sum();
            messageBlocker.unblockPlayer(player).thenRun(() -> sendReceived(player, order.amount(), waiting));
        });
    }

    private void keep(Player player, List<ItemAmount> items) {
        for (var item : items) {
            orderData.submitItemReturn(player.getUniqueId(), ReturnKind.OWNER, item.stack(), item.amount());
        }
    }

    private void sendReceived(Player player, int total, int waiting) {
        if (waiting <= 0) {
            messageSender().sendMessage(player, "order.receive.received");
            return;
        }
        var composer = MessageComposer.create()
                .localeCode(
                        "order.receive.receivedPartly",
                        Replacement.create("received", total - waiting),
                        Replacement.create("waiting", waiting))
                .space()
                .text("<click:run_command:/order collect><add>[")
                .localeCode("words.collect")
                .text("]</click>");
        messageSender().sendMessage(player, composer);
    }
}
