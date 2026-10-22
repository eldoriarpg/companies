/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.OwnOpenOrderIds;
import de.eldoria.companies.commands.suggest.SuggestionSources;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.orders.Collector;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.messages.Replacement;
import net.milkbowl.vault.economy.Economy;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.logging.Level;

/**
 * {@code /order cancel <id>}: cancels an unaccepted order of the sender. The price is refunded, the fee goes to the
 * collector.
 */
@Subcommand("cancel")
@PlayerOnly
public final class Cancel {
    private final CommandDependencies deps;
    private final OwnerOrderViews views;

    /**
     * @param deps  what the commands need
     * @param views shows the orders once the order is canceled
     */
    public Cancel(CommandDependencies deps, OwnerOrderViews views) {
        this.deps = deps;
        this.views = views;
    }

    /**
     * @param sender sender of the command
     * @param id     id of the order
     */
    @Executes
    public void cancel(CommandSender sender, @OwnOpenOrderIds int id) {
        var player = Senders.player(sender);
        var economy = deps.services().economy();
        var collector = new Collector(deps.plugin(), economy, deps.configuration());
        var orderData = deps.orderData();
        var messages = deps.messages();

        orderData
                .retrieveOrderById(id)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(optOrder -> {
                    if (optOrder.isEmpty()) {
                        messages.sendErrorActionBar(player, "error.unknownOrder");
                        return;
                    }
                    var simpleOrder = optOrder.get();
                    if (!simpleOrder.owner().equals(player.getUniqueId())) {
                        messages.sendError(player, "error.notYourOrder");
                        return;
                    }
                    if (simpleOrder.state() != OrderState.UNCLAIMED) {
                        messages.sendError(player, "error.orderAlreadyClaimed");
                        return;
                    }
                    var fullOrder = orderData.retrieveFullOrder(simpleOrder).join();
                    if (fullOrder == null) {
                        messages.sendErrorActionBar(player, "error.unknownOrder");
                        return;
                    }
                    if (!Boolean.TRUE.equals(
                            orderData.submitOrderDeletion(fullOrder).join())) {
                        messages.sendError(player, "error.orderAlreadyClaimed");
                        return;
                    }
                    SuggestionSources.current()
                            .ifPresent(sources -> sources.ownOrders().invalidate(player.getUniqueId()));
                    Threading.mainThread(deps.plugin()).execute(() -> economy.depositPlayer(player, fullOrder.price()));
                    collector.collect(fullOrder.fee());
                    views.showOrders(player, () -> sendCanceled(player, fullOrder, economy));
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    private void sendCanceled(Player player, FullOrder order, Economy economy) {
        var money = Replacement.create("money", economy.format(order.price()));
        if (order.fee() > 0) {
            deps.messages()
                    .sendMessage(
                            player,
                            "order.cancel.canceledFeeKept",
                            money,
                            Replacement.create("fee", economy.format(order.fee())));
            return;
        }
        deps.messages().sendMessage(player, "order.cancel.canceled", money);
    }
}
