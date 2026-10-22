/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.util.Texts;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.messages.Replacement;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Restarts the purge timer of an order nobody accepted yet, up to {@code maxExtensions} times per order.
 */
public class Extend extends AdvancedCommand implements IPlayerTabExecutor {
    private final AOrderData orderData;
    private final Configuration configuration;

    public Extend(Plugin plugin, AOrderData orderData, Configuration configuration) {
        super(
                plugin,
                CommandMeta.builder("extend").addArgument("words.id", true).build());
        this.orderData = orderData;
        this.configuration = configuration;
    }

    /**
     * How many extensions an order has left.
     *
     * @param order         order to check
     * @param maxExtensions extensions an order may have
     * @return extensions left, never negative
     */
    public static int extensionsLeft(SimpleOrder order, int maxExtensions) {
        return Math.max(0, maxExtensions - order.extensions());
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments)
            throws CommandException {
        var id = arguments.asInt(0);
        var maxExtensions = configuration.orderSetting().maxExtensions();

        orderData
                .retrieveOrderById(id)
                .asFuture()
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(optOrder -> {
                    if (optOrder.isEmpty() || !optOrder.get().owner().equals(player.getUniqueId())) {
                        messageSender().sendErrorActionBar(player, "error.unknownOrder");
                        return;
                    }
                    var order = optOrder.get();
                    if (order.state() != OrderState.UNCLAIMED) {
                        messageSender().sendError(player, "error.orderAlreadyClaimed");
                        return;
                    }
                    if (extensionsLeft(order, maxExtensions) == 0
                            || !Boolean.TRUE.equals(orderData
                                    .submitOrderExtension(order, maxExtensions)
                                    .join())) {
                        messageSender().sendError(player, "order.extend.noneLeft");
                        return;
                    }
                    var refundIn = Duration.ofHours(configuration.orderSetting().maxUnclaimedHours());
                    messageSender()
                            .sendMessage(
                                    player,
                                    "order.extend.extended",
                                    Replacement.unparsed("order_name", order.fullName()),
                                    Replacement.create("duration", Texts.prettyDuration(refundIn)),
                                    Replacement.create("left", extensionsLeft(order, maxExtensions) - 1));
                })
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }
}
