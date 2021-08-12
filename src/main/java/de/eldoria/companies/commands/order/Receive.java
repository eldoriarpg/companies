package de.eldoria.companies.commands.order;

import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.orders.OrderState;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.utils.Parser;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class Receive extends EldoCommand {
    private final AOrderData orderData;

    public Receive(Plugin plugin, AOrderData orderData) {
        super(plugin);
        this.orderData = orderData;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (denyConsole(sender)) return true;
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
                    if (simpleOrder.state() != OrderState.DELIVERED) {
                        messageSender().sendLocalizedError(sender, "Not ready");
                        return;
                    }
                    orderData.retrieveFullOrder(optOrder.get())
                            .whenComplete(fullOrder -> {
                                var stacks = fullOrder.createStacks();
                                var empty = 0;
                                for (var content : player.getInventory().getContents()) {
                                    if (content == null) empty++;
                                }

                                if (stacks.size() > empty) {
                                    messageSender().sendLocalizedError(sender, "Not enought space. You need " + stacks.size() + " slots");
                                    return;
                                }
                                player.getInventory().addItem(stacks.toArray(ItemStack[]::new));
                                orderData.submitOrderStateUpdate(fullOrder, OrderState.RECEIVED);
                            });
                });
        return true;
    }
}
