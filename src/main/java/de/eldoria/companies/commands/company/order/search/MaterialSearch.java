package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.commands.company.order.Search;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.orders.OrderState;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class MaterialSearch extends EldoCommand {
    private final AOrderData orderData;
    private final Search search;

    public MaterialSearch(Plugin plugin, AOrderData orderData, Search search) {
        super(plugin);
        this.orderData = orderData;
        this.search = search;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (argumentsInvalid(sender, args, 1, "<material>")) return true;
        if (denyConsole(sender)) return true;
        var player = getPlayerFromSender(sender);

        var material = String.join("_", args);

        orderData.retrieveOrdersByMaterial(material, OrderState.UNCLAIMED, OrderState.UNCLAIMED)
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
