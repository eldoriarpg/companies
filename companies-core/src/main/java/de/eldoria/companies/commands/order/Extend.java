/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.OwnOpenOrderIds;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.util.Texts;
import de.eldoria.eldoutilities.messages.Replacement;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;

import java.time.Duration;
import java.util.Optional;
import java.util.logging.Level;

/**
 * {@code /order extend <id>}: restarts the purge timer of an order nobody accepted yet, up to {@code maxExtensions}
 * times per order.
 */
@Subcommand("extend")
@PlayerOnly
public final class Extend {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public Extend(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * @param sender sender of the command
     * @param id     id of the order
     */
    @Executes
    public void extend(CommandSender sender, @OwnOpenOrderIds int id) {
        var player = Senders.player(sender);
        deps.requireReady();
        var orderSettings = deps.configuration().orderSetting();
        var maxExtensions = orderSettings.maxExtensions();
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
                    if (optOrder.isEmpty() || !optOrder.get().owner().equals(player.getUniqueId())) {
                        messages.sendErrorActionBar(player, "error.unknownOrder");
                        return;
                    }
                    var order = optOrder.get();
                    if (order.state() != OrderState.UNCLAIMED) {
                        messages.sendError(player, "error.orderAlreadyClaimed");
                        return;
                    }
                    if (order.extensionsLeft(maxExtensions) == 0
                            || !Boolean.TRUE.equals(orderData
                                    .submitOrderExtension(order, maxExtensions)
                                    .join())) {
                        messages.sendError(player, "order.extend.noneLeft");
                        return;
                    }
                    var refundIn = Duration.ofHours(orderSettings.maxUnclaimedHours());
                    messages.sendMessage(
                            player,
                            "order.extend.extended",
                            Replacement.unparsed("order_name", order.fullName()),
                            Replacement.create("duration", Texts.prettyDuration(refundIn)),
                            Replacement.create("left", order.extensionsLeft(maxExtensions) - 1));
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }
}
