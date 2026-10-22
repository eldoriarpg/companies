/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.util.Menus;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.logging.Level;

public class Info extends AdvancedCommand implements IPlayerTabExecutor {
    private final AOrderData orderData;
    private final Economy economy;
    private final Configuration configuration;
    private final MessageBlocker messageBlocker;

    public Info(Plugin plugin, AOrderData orderData, Economy economy, Configuration configuration, MessageBlocker messageBlocker) {
        super(plugin, CommandMeta.builder("info")
                .addArgument("id", true)
                .build());
        this.orderData = orderData;
        this.economy = economy;
        this.configuration = configuration;
        this.messageBlocker = messageBlocker;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments args) throws CommandException {
        var id = args.asInt(0);

        orderData.retrieveOrderById(id)
                 .asFuture()
                 .exceptionally(err -> {
                     plugin().getLogger()
                             .log(Level.SEVERE, "Something went wrong", err);
                     return Optional.empty();
                 })
                 .thenAccept((order) -> {
                     if (order.isPresent() && order.get().owner().equals(player.getUniqueId())) {
                         messageBlocker.blockPlayer(player);
                         var fullOrder = orderData.retrieveFullOrder(order.get())
                                                  .join();
                         var builder = fullOrder.userDetailInfo(economy, configuration);
                         if (messageBlocker.isBlocked(player)) {
                             builder.newLine()
                                    .text("<click:run_command:/company chatblock false><red>[x]</red></click>");
                         }
                         builder.prependLines(25);
                         Menus.show(messageBlocker, player, builder);
                         return;
                     }
                     messageSender().sendErrorActionBar(player, "error.unkownOrder");
                 });
    }
}
