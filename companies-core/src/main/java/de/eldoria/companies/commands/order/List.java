/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.util.Colors;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.logging.Level;

public class List extends AdvancedCommand implements IPlayerTabExecutor {
    private final AOrderData orderData;
    private final Economy economy;
    private final BukkitAudiences audiences;
    private final MiniMessage miniMessage;
    private final Configuration configuration;
    private final MessageBlocker messageBlocker;

    public List(Plugin plugin, AOrderData orderData, Economy economy, Configuration configuration, MessageBlocker messageBlocker) {
        super(plugin, CommandMeta.builder("list").build());
        this.orderData = orderData;
        this.economy = economy;
        audiences = BukkitAudiences.create(plugin);
        miniMessage = MiniMessage.miniMessage();
        this.configuration = configuration;
        this.messageBlocker = messageBlocker;
    }

    public void showOrders(Player player) {
        showOrders(player, () -> {
        });
    }

    public void showOrders(Player player, Runnable whenComplete) {
        orderData.retrieveOrdersByPlayer(player, OrderState.UNCLAIMED, OrderState.DELIVERED)
                .asFuture()
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Collections.emptyList();
                }).thenApply(orders -> orderData.retrieveFullOrders(orders).asFuture()
                        .exceptionally(err -> {
                            plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                            return Collections.emptyList();
                        })
                        .join())
                .thenAccept((orders -> {
                    messageBlocker.blockPlayer(player);
                    var builder = MessageComposer.create().localeCode("order.list.orders").text(":").newLine();
                    if (configuration.userSettings().maxOrders() > orders.size()) {
                        builder.text("<click:suggest_command:/order create ><add>[").localeCode("order.list.newOrder").text("]</click>");
                    }
                    for (var order : orders) {
                        builder.newLine().text(order.userShortInfo(economy));
                    }
                    if (messageBlocker.isBlocked(player)) {
                        builder.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
                    }
                    messageBlocker.announce(player, "[x]");
                    builder.prependLines(25);
                    audiences.player(player).sendMessage(miniMessage.deserialize(localizer().localize(builder.build())));
                    whenComplete.run();
                }));
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        showOrders(player);
    }
}
