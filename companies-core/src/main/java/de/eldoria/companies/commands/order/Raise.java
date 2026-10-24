/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.OwnOpenOrderIds;
import de.eldoria.companies.commands.support.Checks;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.menus.ConfirmFlow;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.OwnerOrderService;
import de.eldoria.companies.services.ActionResult;
import de.eldoria.companies.util.Money;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.messages.Replacement;
import net.milkbowl.vault.economy.Economy;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.DoubleArg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * {@code /order raise <id> <amount>}: raises the price of an order nobody accepted yet.
 * <p>
 * The owner pays the increase plus the percentage fee on it; the fixed fee is not charged again and the fee bounds
 * apply to the order's total fee. The owner confirms the amount first, and the money is only kept when the order
 * could still be changed. The checks and the raise are made by {@link OwnerOrderService}.
 */
@Subcommand("raise")
@PlayerOnly
public final class Raise {
    private final CommandDependencies deps;
    private final ConfirmFlow<Request> requests;

    /**
     * @param deps what the commands need
     */
    public Raise(CommandDependencies deps) {
        this.deps = deps;
        this.requests = ConfirmFlow.create(deps.plugin(), this::raiseConfirmed);
    }

    /**
     * Asks the owner to confirm the increase and what it costs.
     *
     * @param sender sender of the command
     * @param id     id of the order
     * @param amount increase of the price
     */
    @Executes
    public void raise(
            CommandSender sender, @OwnOpenOrderIds int id, @DoubleArg(min = 0, max = Double.MAX_VALUE) double amount) {
        var player = Senders.player(sender);
        var economy = deps.services().economy();
        var menus = deps.services().menus();
        var increase = Money.floor(amount);
        Checks.isTrue(increase > 0, "error.invalidNumber");

        deps.services()
                .ownerOrders()
                .raiseQuote(player.getUniqueId(), id, increase)
                .thenAccept(result -> {
                    switch (result) {
                        case ActionResult.Refused<OwnerOrderService.Raise> refused ->
                            refused.failure().sendTo(player, deps.messages());
                        case ActionResult.Done<OwnerOrderService.Raise>(var quote) ->
                            requests.ask(menus, player, new Request(id, increase), confirmation(quote, economy));
                    }
                })
                .exceptionally(this::log);
    }

    /**
     * Raises the price the owner confirmed.
     *
     * @param sender sender of the command
     */
    @Executes("confirm")
    public void confirm(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        requests.confirm(player);
    }

    private void raiseConfirmed(Player player, Request request) {
        var economy = deps.services().economy();
        deps.services()
                .ownerOrders()
                .raise(player.getUniqueId(), request.order(), request.increase())
                .thenAcceptAsync(
                        result -> {
                            switch (result) {
                                case ActionResult.Refused<OwnerOrderService.Raise> refused ->
                                    refused.failure().sendTo(player, deps.messages());
                                case ActionResult.Done<OwnerOrderService.Raise>(var raised) ->
                                    deps.messages()
                                            .sendMessage(
                                                    player,
                                                    "order.raise.raised",
                                                    Replacement.unparsed(
                                                            "order_name",
                                                            raised.order().fullName()),
                                                    Replacement.create("price", economy.format(raised.newPrice())));
                            }
                        },
                        Threading.mainThread(deps.plugin()))
                .exceptionally(this::log);
    }

    private static MenuText confirmation(OwnerOrderService.Raise quote, Economy economy) {
        var question = MenuText.create()
                .locale(
                        "order.raise.confirm",
                        Values.text("order_name", quote.order().fullName()),
                        Values.money("amount", economy, quote.increase()),
                        Values.money("total", economy, quote.total()));
        if (quote.fee() > 0) {
            question.space().locale("order.raise.fee", Values.money("fee", economy, quote.fee()));
        }
        return question;
    }

    private Void log(Throwable err) {
        deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
        return null;
    }

    private record Request(int order, double increase) {}
}
