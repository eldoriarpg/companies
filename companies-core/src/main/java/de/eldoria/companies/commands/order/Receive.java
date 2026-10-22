/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.util.Inventories;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.messages.Replacement;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

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

    private void receive(Player player, FullOrder order) {
        if (!player.isOnline()) return;
        var stacks = order.createStacks();
        var storage = Inventories.storageCopy(player);
        var fits = stacks.stream()
                .allMatch(stack -> Inventories.fit(storage, stack, stack.getAmount()) == stack.getAmount());
        if (!fits) {
            messageSender()
                    .sendError(player, "order.receive.inventoryFull", Replacement.create("amount", stacks.size()));
            return;
        }
        orderData
                .submitOrderStateUpdate(order, OrderState.DELIVERED, OrderState.RECEIVED)
                .whenComplete(received -> {
                    if (!received) {
                        messageSender().sendError(player, "error.orderNotDelivered");
                        return;
                    }
                    if (!player.isOnline()) {
                        for (var content : order.contents()) {
                            orderData.submitItemReturn(player.getUniqueId(), content.stack(), content.amount());
                        }
                        return;
                    }
                    Inventories.give(player, stacks);
                    messageBlocker.unblockPlayer(player).thenRun(() -> messageSender()
                            .sendMessage(player, "order.receive.received"));
                });
    }
}
