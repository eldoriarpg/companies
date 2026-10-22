/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.OwnOrderIds;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.util.Menus;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;

import java.util.Optional;
import java.util.logging.Level;

/**
 * {@code /order info <id>}: one of the sender's orders with its progress, price and the owner's actions.
 */
@Subcommand("info")
@PlayerOnly
public final class Info {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public Info(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * @param sender sender of the command
     * @param id     id of the order
     */
    @Executes
    public void info(CommandSender sender, @OwnOrderIds int id) {
        var player = Senders.player(sender);
        var services = deps.services();
        var messageBlocker = services.messageBlocker();
        var orderData = deps.orderData();

        orderData
                .retrieveOrderById(id)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(order -> {
                    if (order.isEmpty() || !order.get().owner().equals(player.getUniqueId())) {
                        deps.messages().sendErrorActionBar(player, "error.unknownOrder");
                        return;
                    }
                    var fullOrder = orderData.retrieveFullOrder(order.get()).join();
                    if (fullOrder == null) {
                        deps.messages().sendErrorActionBar(player, "error.unknownOrder");
                        return;
                    }
                    messageBlocker.blockPlayer(player);
                    var builder = fullOrder.userDetailInfo(services.economy(), deps.configuration());
                    if (messageBlocker.isBlocked(player)) {
                        builder.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
                    }
                    builder.prependLines(25);
                    Menus.show(messageBlocker, player, builder);
                });
    }
}
