package de.eldoria.companies.commands.order;

import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.orders.OrderState;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.utils.Parser;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class Cancel extends EldoCommand {
    private final AOrderData orderData;
    private final Economy economy;

    public Cancel(Plugin plugin, AOrderData orderData, Economy economy) {
        super(plugin);
        this.orderData = orderData;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (argumentsInvalid(sender, args, 1, "<id>")) return true;
        var optId = Parser.parseInt(args[0]);

        orderData.retrieveOrderById(optId.getAsInt())
                .whenComplete(optOrder -> {
                    if (optOrder.isEmpty()) {
                        messageSender().sendError(sender, "Order not found.");
                        return;
                    }

                    var simpleOrder = optOrder.get();
                    var player = getPlayerFromSender(sender);
                    if (!simpleOrder.owner().equals(player)) {
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
                                orderData.submitOrderDeletion(fullOrder);
                                messageSender().sendMessage(sender, "Order canceled. You got your " + economy.format(fullOrder.price()) + " back.");
                            });
                });
        return true;
    }
}
