/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.orders.OrderLimits;
import de.eldoria.companies.util.Menus;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.Replacement;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.logging.Level;

public class List extends AdvancedCommand implements IPlayerTabExecutor {
    private final AOrderData orderData;
    private final Economy economy;
    private final OrderLimits orderLimits;
    private final MessageBlocker messageBlocker;

    public List(
            Plugin plugin,
            AOrderData orderData,
            OrderLimits orderLimits,
            Economy economy,
            MessageBlocker messageBlocker) {
        super(plugin, CommandMeta.builder("list").build());
        this.orderData = orderData;
        this.economy = economy;
        this.orderLimits = orderLimits;
        this.messageBlocker = messageBlocker;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments)
            throws CommandException {
        showOrders(player);
    }

    public void showOrders(Player player) {
        showOrders(player, () -> {});
    }

    public void showOrders(Player player, Runnable whenComplete) {
        var usage = orderLimits.usage(player).asFuture();
        var waiting = orderData
                .retrieveItemReturnCount(player.getUniqueId(), ReturnKind.OWNER)
                .asFuture()
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Could not count waiting order items", err);
                    return 0;
                });
        orderData
                .retrieveOrdersByPlayer(player, OrderState.UNCLAIMED, OrderState.DELIVERED)
                .asFuture()
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Collections.emptyList();
                })
                .thenApply(orders -> orderData
                        .retrieveFullOrders(orders)
                        .asFuture()
                        .exceptionally(err -> {
                            plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                            return Collections.emptyList();
                        })
                        .join())
                .thenCombine(usage, OrdersView::new)
                .thenCombine(waiting, OrdersView::withWaiting)
                .thenAccept(view -> {
                    var orders = view.orders();
                    var limits = view.usage();
                    messageBlocker.blockPlayer(player);
                    var builder = MessageComposer.create()
                            .localeCode("order.list.orders")
                            .text(" (%s/%s):", limits.active(), limits.limit())
                            .newLine();
                    if (limits.reached()) {
                        builder.text("<inactive>").localeCode("order.list.limitReached");
                    } else {
                        builder.text("<click:suggest_command:/order create ><add>[")
                                .localeCode("order.list.newOrder")
                                .text("]</click>");
                    }
                    if (view.waiting() > 0) {
                        builder.newLine()
                                .text("<neutral>")
                                .localeCode("order.list.waiting", Replacement.create("amount", view.waiting()))
                                .space()
                                .text("<click:run_command:/order collect><add>[")
                                .localeCode("words.collect")
                                .text("]</click>");
                    }
                    for (var order : orders) {
                        builder.newLine().text(order.userShortInfo(economy));
                    }
                    if (messageBlocker.isBlocked(player)) {
                        builder.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
                    }
                    builder.prependLines(25);
                    Menus.show(messageBlocker, player, builder);
                    whenComplete.run();
                })
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    private record OrdersView(java.util.List<FullOrder> orders, OrderLimits.Usage usage, int waiting) {
        private OrdersView(java.util.List<FullOrder> orders, OrderLimits.Usage usage) {
            this(orders, usage, 0);
        }

        private OrdersView withWaiting(int waiting) {
            return new OrdersView(orders, usage, waiting);
        }
    }
}
