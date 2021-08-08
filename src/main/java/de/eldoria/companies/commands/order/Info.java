package de.eldoria.companies.commands.order;

import de.eldoria.companies.data.OrderData;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.utils.Parser;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class Info extends EldoCommand {
    private final OrderData orderData;
    private final Economy economy;
    private final BukkitAudiences audiences;

    public Info(Plugin plugin, OrderData orderData, Economy economy) {
        super(plugin);
        this.orderData = orderData;
        this.economy = economy;
        this.audiences = BukkitAudiences.create(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (argumentsInvalid(sender, args, 1, "<id>")) return true;

        var optId = Parser.parseInt(args[0]);

        orderData.retrieveOrderById(optId.getAsInt())
                .whenComplete(order -> {
                    if (order.isPresent()) {
                        orderData.retrieveFullOrder(order.get())
                                .whenComplete(fullOrder -> audiences.sender(sender)
                                        .sendMessage(fullOrder.userDetailInfo(localizer(), economy)));
                        return;
                    }
                    messageSender().sendError(sender, "Order not found.");
                });
        return true;
    }
}
