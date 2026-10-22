/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.OwnOpenOrderIds;
import de.eldoria.companies.commands.support.Checks;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.orders.PriceRaise;
import de.eldoria.companies.util.Confirmations;
import de.eldoria.companies.util.Money;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.Replacement;
import net.milkbowl.vault.economy.Economy;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.DoubleArg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * {@code /order raise <id> <amount>}: raises the price of an order nobody accepted yet.
 * <p>
 * The owner pays the increase plus the percentage fee on it; the fixed fee is not charged again and the fee bounds
 * apply to the order's total fee. The owner confirms the amount first, and the money is only kept when the order
 * could still be changed.
 */
@Subcommand("raise")
@PlayerOnly
public final class Raise {
    private final CommandDependencies deps;
    private final Confirmations<Request> requests = new Confirmations<>();

    /**
     * @param deps what the commands need
     */
    public Raise(CommandDependencies deps) {
        this.deps = deps;
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
        var increase = Money.floor(amount);
        Checks.isTrue(increase > 0, "error.invalidNumber");

        loadOrder(player, id)
                .thenAccept(optOrder -> optOrder.ifPresent(order -> {
                    var fee = extraFee(order, increase);
                    requests.request(player.getUniqueId(), new Request(id, increase));
                    deps.messages().sendMessage(player, confirmation(order, increase, fee, economy));
                }));
    }

    /**
     * Raises the price the owner confirmed.
     *
     * @param sender sender of the command
     */
    @Executes("confirm")
    public void confirm(CommandSender sender) {
        var player = Senders.player(sender);
        var economy = deps.services().economy();
        var optRequest = requests.confirm(player.getUniqueId());
        if (optRequest.isEmpty()) {
            deps.messages().sendErrorActionBar(player, "error.confirmationExpired");
            return;
        }
        var request = optRequest.get();
        loadOrder(player, request.order())
                .thenAcceptAsync(
                        optOrder -> optOrder.ifPresent(order -> charge(player, order, request.increase(), economy)),
                        Threading.mainThread(deps.plugin()));
    }

    private MessageComposer confirmation(FullOrder order, double increase, double fee, Economy economy) {
        var composer = MessageComposer.create()
                .text("<neutral>")
                .localeCode(
                        "order.raise.confirm",
                        Replacement.unparsed("order_name", order.fullName()),
                        Replacement.create("amount", economy.format(increase)),
                        Replacement.create("total", economy.format(increase + fee)));
        if (fee > 0) {
            composer.space().localeCode("order.raise.fee", Replacement.create("fee", economy.format(fee)));
        }
        return composer.space()
                .text("<click:run_command:/order raise confirm><add>[")
                .localeCode("words.confirm")
                .text("]</click>");
    }

    private void charge(Player player, FullOrder order, double increase, Economy economy) {
        if (!player.isOnline()) return;
        var fee = extraFee(order, increase);
        var total = increase + fee;
        if (!economy.has(player, total)
                || !economy.withdrawPlayer(player, total).transactionSuccess()) {
            deps.messages()
                    .sendError(
                            player,
                            "error.insufficientCurrency",
                            Replacement.create("currency", Money.currencyName(economy)),
                            Replacement.create("amount", economy.format(total)));
            return;
        }
        deps.orderData()
                .submitPriceRaise(order, increases(order, increase), fee)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not raise the price of an order", err);
                    return false;
                })
                .thenAcceptAsync(
                        raised -> {
                            if (!raised) {
                                economy.depositPlayer(player, total);
                                deps.messages().sendError(player, "error.orderAlreadyClaimed");
                                return;
                            }
                            deps.messages()
                                    .sendMessage(
                                            player,
                                            "order.raise.raised",
                                            Replacement.unparsed("order_name", order.fullName()),
                                            Replacement.create("price", economy.format(order.price() + increase)));
                        },
                        Threading.mainThread(deps.plugin()));
    }

    private double extraFee(FullOrder order, double increase) {
        return deps.configuration().orderSetting().fee().raise(order.fee(), increase);
    }

    private static Map<Integer, Double> increases(FullOrder order, double increase) {
        var contents = order.contents();
        var shares = PriceRaise.split(contents.stream().map(OrderContent::price).toList(), increase);
        Map<Integer, Double> increases = new HashMap<>();
        for (var i = 0; i < contents.size(); i++) {
            increases.merge(contents.get(i).line(), shares.get(i), Double::sum);
        }
        return increases;
    }

    /**
     * Loads an unclaimed order of the player with its contents and reports why it can not be raised otherwise.
     *
     * @param player owner of the order
     * @param id     id of the order
     * @return the order, or empty when the player can not raise its price
     */
    private CompletableFuture<Optional<FullOrder>> loadOrder(Player player, int id) {
        var orderData = deps.orderData();
        return orderData
                .retrieveOrderById(id)
                .asFuture()
                .thenApply(optOrder -> {
                    if (optOrder.isEmpty() || !optOrder.get().owner().equals(player.getUniqueId())) {
                        deps.messages().sendErrorActionBar(player, "error.unknownOrder");
                        return Optional.<FullOrder>empty();
                    }
                    if (optOrder.get().state() != OrderState.UNCLAIMED) {
                        deps.messages().sendError(player, "error.orderAlreadyClaimed");
                        return Optional.<FullOrder>empty();
                    }
                    return Optional.ofNullable(
                            orderData.retrieveFullOrder(optOrder.get()).join());
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                });
    }

    private record Request(int order, double increase) {}
}
