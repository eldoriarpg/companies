/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandMetaBuilder;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.messages.Replacement;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class Cancel extends AdvancedCommand implements IPlayerTabExecutor {
    private final AOrderData orderData;
    private final Economy economy;
    private final List list;

    public Cancel(Plugin plugin, AOrderData orderData, Economy economy, List list) {
        super(plugin, new CommandMetaBuilder("cancel")
                .addArgument("words.id", true)
                .build());
        this.orderData = orderData;
        this.economy = economy;
        this.list = list;
    }

    @Override
    public void onCommand(@NotNull Player sender, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        var id = arguments.asInt(0);

        orderData.retrieveOrderById(id)
                .asFuture()
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(optOrder -> {
                    if (optOrder.isEmpty()) {
                        messageSender().sendErrorActionBar(sender, "error.unkownOrder");
                        return;
                    }

                    var simpleOrder = optOrder.get();
                    var player = getPlayerFromSender(sender);
                    if (!simpleOrder.owner().equals(player.getUniqueId())) {
                        messageSender().sendError(sender, "error.notYourOrder");
                        return;
                    }
                    if (simpleOrder.state() != OrderState.UNCLAIMED) {
                        messageSender().sendError(sender, "error.orderAlreadyClaimed");
                        return;
                    }

                    var fullOrder = orderData.retrieveFullOrder(optOrder.get()).join();
                    CompletableFuture.runAsync(() -> economy.depositPlayer(player, fullOrder.price()));
                    orderData.submitOrderDeletion(fullOrder).join();
                    list.showOrders(player, () -> {
                        messageSender().sendError(sender, "order.cancel.canceled",
                                Replacement.create("money", economy.format(fullOrder.price())));
                    });
                }).exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                })
        ;
    }
}
