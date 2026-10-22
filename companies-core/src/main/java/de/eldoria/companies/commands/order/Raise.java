/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.orders.PriceRaise;
import de.eldoria.companies.util.Confirmations;
import de.eldoria.companies.util.Money;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Argument;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.Replacement;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Raises the price of an order nobody accepted yet.
 * <p>
 * The owner pays the increase plus the percentage fee on it; the fixed fee is not charged again and the fee bounds
 * apply to the order's total fee. The owner confirms the amount first, and the money is only kept when the order
 * could still be changed.
 */
public class Raise extends AdvancedCommand implements IPlayerTabExecutor {
    private final AOrderData orderData;
    private final Economy economy;
    private final Configuration configuration;
    private final Confirmations<Request> requests = new Confirmations<>();

    public Raise(Plugin plugin, AOrderData orderData, Economy economy, Configuration configuration) {
        super(
                plugin,
                CommandMeta.builder("raise")
                        .addArgument("words.id", true)
                        .addArgument("words.amount", false)
                        .build());
        this.orderData = orderData;
        this.economy = economy;
        this.configuration = configuration;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments)
            throws CommandException {
        if ("confirm".equalsIgnoreCase(arguments.asString(0))) {
            confirm(player);
            return;
        }
        var id = arguments.asInt(0);
        CommandAssertions.invalidArguments(
                meta(), arguments, Argument.input("words.id", true), Argument.input("words.amount", true));
        var amount = arguments.asDouble(1);
        if (!Double.isFinite(amount)) CommandAssertions.invalidNumber();
        var increase = Money.floor(amount);
        if (!(increase > 0)) CommandAssertions.invalidNumber();

        loadOrder(player, id)
                .thenAccept(optOrder -> optOrder.ifPresent(order -> {
                    var fee = extraFee(order, increase);
                    requests.request(player.getUniqueId(), new Request(id, increase));
                    messageSender().sendMessage(player, confirmation(order, increase, fee));
                }));
    }

    private MessageComposer confirmation(FullOrder order, double increase, double fee) {
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

    private void confirm(Player player) {
        var optRequest = requests.confirm(player.getUniqueId());
        if (optRequest.isEmpty()) {
            messageSender().sendErrorActionBar(player, "error.confirmationExpired");
            return;
        }
        var request = optRequest.get();
        loadOrder(player, request.order())
                .thenAcceptAsync(
                        optOrder -> optOrder.ifPresent(order -> charge(player, order, request.increase())),
                        Threading.mainThread(plugin()));
    }

    private void charge(Player player, FullOrder order, double increase) {
        if (!player.isOnline()) return;
        var fee = extraFee(order, increase);
        var total = increase + fee;
        if (!economy.has(player, total)
                || !economy.withdrawPlayer(player, total).transactionSuccess()) {
            messageSender()
                    .sendError(
                            player,
                            "error.insufficientCurrency",
                            Replacement.create("currency", Money.currencyName(economy)),
                            Replacement.create("amount", economy.format(total)));
            return;
        }
        orderData
                .submitPriceRaise(order, increases(order, increase), fee)
                .asFuture()
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Could not raise the price of an order", err);
                    return false;
                })
                .thenAcceptAsync(
                        raised -> {
                            if (!raised) {
                                economy.depositPlayer(player, total);
                                messageSender().sendError(player, "error.orderAlreadyClaimed");
                                return;
                            }
                            messageSender()
                                    .sendMessage(
                                            player,
                                            "order.raise.raised",
                                            Replacement.unparsed("order_name", order.fullName()),
                                            Replacement.create("price", economy.format(order.price() + increase)));
                        },
                        Threading.mainThread(plugin()));
    }

    private double extraFee(FullOrder order, double increase) {
        return configuration.orderSetting().fee().raise(order.fee(), increase);
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
        return orderData
                .retrieveOrderById(id)
                .asFuture()
                .thenApply(optOrder -> {
                    if (optOrder.isEmpty() || !optOrder.get().owner().equals(player.getUniqueId())) {
                        messageSender().sendErrorActionBar(player, "error.unknownOrder");
                        return Optional.<FullOrder>empty();
                    }
                    if (optOrder.get().state() != OrderState.UNCLAIMED) {
                        messageSender().sendError(player, "error.orderAlreadyClaimed");
                        return Optional.<FullOrder>empty();
                    }
                    return Optional.ofNullable(
                            orderData.retrieveFullOrder(optOrder.get()).join());
                })
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                });
    }

    private record Request(int order, double increase) {}
}
