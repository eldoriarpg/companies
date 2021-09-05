package de.eldoria.companies.commands.order;

import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.orders.OrderState;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandMetaBuilder;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class Cancel extends AdvancedCommand implements IPlayerTabExecutor {
    private final AOrderData orderData;
    private final Economy economy;
    private final List list;

    public Cancel(Plugin plugin, AOrderData orderData, Economy economy, List list) {
        super(plugin, new CommandMetaBuilder("cancel")
                .addArgument("id", true)
                .build());
        this.orderData = orderData;
        this.economy = economy;
        this.list = list;
    }

    @Override
    public void onCommand(@NotNull Player sender, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        var id = arguments.asInt(0);

        orderData.retrieveOrderById(id)
                .whenComplete(optOrder -> {
                    if (optOrder.isEmpty()) {
                        messageSender().sendError(sender, "Order not found.");
                        return;
                    }

                    var simpleOrder = optOrder.get();
                    var player = getPlayerFromSender(sender);
                    if (!simpleOrder.owner().equals(player.getUniqueId())) {
                        messageSender().sendLocalizedError(sender, "Not your order");
                        return;
                    }
                    if (simpleOrder.state() != OrderState.UNCLAIMED) {
                        messageSender().sendLocalizedError(sender, "Order is already claimed");
                        return;
                    }

                    orderData.retrieveFullOrder(optOrder.get())
                            .whenComplete(fullOrder -> {
                                CompletableFuture.runAsync(() -> economy.depositPlayer(player, fullOrder.price()));
                                orderData.submitOrderDeletion(fullOrder).whenComplete(r -> {
                                    list.showOrders(player, () -> {
                                        messageSender().sendMessage(sender, "Order canceled. You got your " + economy.format(fullOrder.price()) + " back.");
                                    });
                                });
                            });
                });
    }

    @Override
    public java.util.@Nullable List<String> onTabComplete(@NotNull Player sender, @NotNull String alias, @NotNull Arguments arguments) {
        return null;
    }
}
