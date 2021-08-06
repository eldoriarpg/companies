package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.commands.company.order.Search;
import de.eldoria.companies.data.CompanyData;
import de.eldoria.companies.data.OrderData;
import de.eldoria.companies.orders.OrderState;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class All extends EldoCommand {
    private final OrderData orderData;
    private final Search search;

    public All(Plugin plugin, OrderData orderData, Search search) {
        super(plugin);
        this.orderData = orderData;
        this.search = search;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (denyConsole(sender)) return true;
        var player = getPlayerFromSender(sender);

        orderData.retrieveOrdersByState(OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                .whenComplete(orders -> {
                    orderData.retrieveFullOrders(orders)
                            .whenComplete(fullOrders -> {
                                search.results().put(player.getUniqueId(), fullOrders);
                                search.page().renderPage(player, 0);
                            });
                });
        return true;
    }
}
