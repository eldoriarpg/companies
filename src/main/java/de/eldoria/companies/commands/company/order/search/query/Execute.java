package de.eldoria.companies.commands.company.order.search.query;

import de.eldoria.companies.commands.company.order.Search;
import de.eldoria.companies.commands.company.order.search.Query;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.orders.OrderState;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class Execute extends EldoCommand {
    private final Query query;
    private final AOrderData orderData;
    private final Search search;

    public Execute(Plugin plugin, Query query, Search search, AOrderData orderData) {
        super(plugin);
        this.query = query;
        this.orderData = orderData;
        this.search = search;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        var player = getPlayerFromSender(sender);
        orderData.retrieveOrdersByQuery(query.getPlayerSearch(player), OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                .whenComplete(fullOrders -> {
                    search.results().put(player.getUniqueId(), fullOrders);
                    search.page().renderPage(player, 0);
                });
        return false;
    }
}
