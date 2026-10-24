/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.OwnOpenOrderIds;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.menus.ConfirmFlow;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.OwnerOrderService;
import de.eldoria.companies.orders.OwnerOrderService.Cancellable;
import de.eldoria.companies.services.ActionResult;
import de.eldoria.eldoutilities.messages.Replacement;
import net.milkbowl.vault.economy.Economy;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * {@code /order cancel <id>}: cancels an unaccepted order of the sender. The price is refunded, the fee goes to the
 * collector.
 * <p>
 * Cancelling a copy of an active repeating order stops the repeating order as well. The sender confirms that first,
 * with {@code /order cancel confirm} or the buttons of the confirmation. The checks and the cancel are made by
 * {@link OwnerOrderService}.
 */
@Subcommand("cancel")
@PlayerOnly
public final class Cancel {
    private final CommandDependencies deps;
    private final OwnerOrderViews views;
    private final ConfirmFlow<Integer> copies;

    /**
     * @param deps  what the commands need
     * @param views shows the orders once the order is canceled
     */
    public Cancel(CommandDependencies deps, OwnerOrderViews views) {
        this.deps = deps;
        this.views = views;
        this.copies = ConfirmFlow.create(deps.plugin(), this::cancelNow);
    }

    /**
     * @param sender sender of the command
     * @param id     id of the order
     */
    @Executes
    public void cancel(CommandSender sender, @OwnOpenOrderIds int id) {
        var player = Senders.player(sender);
        var orders = deps.services().ownerOrders();
        orders.cancellable(player.getUniqueId(), id)
                .thenAccept(result -> {
                    switch (result) {
                        case ActionResult.Refused<Cancellable> refused ->
                            refused.failure().sendTo(player, deps.messages());
                        case ActionResult.Done<Cancellable> done -> confirmOrCancel(player, done.value());
                    }
                })
                .exceptionally(this::log);
    }

    /**
     * Cancels the copy of a repeating order the sender confirmed, which stops the repeating order.
     *
     * @param sender sender of the command
     */
    @Executes("confirm")
    public void confirm(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        copies.confirm(player);
    }

    private void confirmOrCancel(Player player, Cancellable cancellable) {
        var order = cancellable.order();
        if (cancellable.activeSeries().isEmpty()) {
            cancelNow(player, order.id());
            return;
        }
        var question = MenuText.create()
                .locale(
                        "order.cancel.stopsSeries",
                        Values.text("order_name", order.fullName()),
                        Values.text(
                                "series_name", cancellable.activeSeries().get().name()));
        copies.ask(deps.services().menus(), player, order.id(), question);
    }

    private void cancelNow(Player player, int id) {
        var economy = deps.services().economy();
        deps.services()
                .ownerOrders()
                .cancel(player.getUniqueId(), id)
                .thenAccept(result -> {
                    switch (result) {
                        case ActionResult.Refused<FullOrder> refused ->
                            refused.failure().sendTo(player, deps.messages());
                        case ActionResult.Done<FullOrder> done ->
                            views.showOrders(player, () -> sendCanceled(player, done.value(), economy));
                    }
                })
                .exceptionally(this::log);
    }

    private Void log(Throwable err) {
        deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
        return null;
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
