package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.commands.company.order.Search;
import de.eldoria.companies.data.CompanyData;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.orders.OrderState;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.utils.EnumUtil;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MaterialSearch extends EldoCommand {
    CompanyData companyData;
    private Search search;

    public MaterialSearch(Plugin plugin, Search search) {
        super(plugin);
        this.search = search;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if(argumentsInvalid(sender, args, 1, "<material>")) return true;
        if (denyConsole(sender)) return true;
        var player = getPlayerFromSender(sender);

        var material = String.join("_", args);

        companyData.retrieveOrdersByMaterial(material, OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                .whenComplete(orders -> {
                    companyData.retrieveFullOrders(orders)
                            .whenComplete(fullOrders -> {
                                search.results().put(player.getUniqueId(), fullOrders);
                                search.page().renderPage(player, 0);
                            });
                });
        return true;
    }
}
