/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.orders.Collector;
import de.eldoria.companies.util.Threading;
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
import java.util.logging.Level;

/**
 * Cancels an unaccepted order of the player. The price is refunded, the fee goes to the collector.
 */
public class Cancel extends AdvancedCommand implements IPlayerTabExecutor {
    private final AOrderData orderData;
    private final Economy economy;
    private final Collector collector;
    private final List list;

    public Cancel(Plugin plugin, AOrderData orderData, Economy economy, Collector collector, List list) {
        super(
                plugin,
                new CommandMetaBuilder("cancel").addArgument("words.id", true).build());
        this.orderData = orderData;
        this.economy = economy;
        this.collector = collector;
        this.list = list;
    }

    @Override
    public void onCommand(@NotNull Player sender, @NotNull String label, @NotNull Arguments arguments)
            throws CommandException {
        var id = arguments.asInt(0);

        orderData
                .retrieveOrderById(id)
                .asFuture()
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(optOrder -> {
                    if (optOrder.isEmpty()) {
                        messageSender().sendErrorActionBar(sender, "error.unknownOrder");
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
                    if (fullOrder == null) {
                        messageSender().sendErrorActionBar(sender, "error.unknownOrder");
                        return;
                    }
                    if (!Boolean.TRUE.equals(
                            orderData.submitOrderDeletion(fullOrder).join())) {
                        messageSender().sendError(sender, "error.orderAlreadyClaimed");
                        return;
                    }
                    Threading.mainThread(plugin()).execute(() -> economy.depositPlayer(player, fullOrder.price()));
                    collector.collect(fullOrder.fee());
                    list.showOrders(player, () -> sendCanceled(sender, fullOrder));
                })
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    private void sendCanceled(Player player, FullOrder order) {
        var money = Replacement.create("money", economy.format(order.price()));
        if (order.fee() > 0) {
            messageSender()
                    .sendMessage(
                            player,
                            "order.cancel.canceledFeeKept",
                            money,
                            Replacement.create("fee", economy.format(order.fee())));
            return;
        }
        messageSender().sendMessage(player, "order.cancel.canceled", money);
    }
}
