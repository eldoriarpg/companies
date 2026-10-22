/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.orders.OrderLimits;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.Replacement;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.logging.Level;

/**
 * {@code /order list}: the sender's orders which are not received yet, with the order limit and the items waiting
 * to be collected.
 */
@Subcommand("list")
@PlayerOnly
public final class List {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public List(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * @param sender sender of the command
     */
    @Executes
    public void list(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        showOrders(player);
    }

    /**
     * Shows the orders of a player.
     *
     * @param player owner of the orders
     */
    public void showOrders(Player player) {
        showOrders(player, () -> {});
    }

    /**
     * Shows the orders of a player and runs an action once they are shown.
     *
     * @param player       owner of the orders
     * @param whenComplete runs after the menu was sent
     */
    public void showOrders(Player player, Runnable whenComplete) {
        var services = deps.services();
        var economy = services.economy();
        var menus = services.menus();
        var orderData = deps.orderData();
        var usage = deps.drafts().limits().usage(player).asFuture();
        var waiting = orderData
                .retrieveItemReturnCount(player.getUniqueId(), ReturnKind.OWNER)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not count waiting order items", err);
                    return 0;
                });
        orderData
                .retrieveOrdersByPlayer(player, OrderState.UNCLAIMED, OrderState.DELIVERED)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Collections.emptyList();
                })
                .thenApply(orders -> orderData
                        .retrieveFullOrders(orders)
                        .asFuture()
                        .exceptionally(err -> {
                            deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                            return Collections.emptyList();
                        })
                        .join())
                .thenCombine(usage, OrdersView::new)
                .thenCombine(waiting, OrdersView::withWaiting)
                .thenAccept(view -> {
                    var limits = view.usage();
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
                    for (var order : view.orders()) {
                        builder.newLine().text(order.userShortInfo(economy));
                    }
                    menus.show(player, builder);
                    whenComplete.run();
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
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
