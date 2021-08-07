package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.commands.company.order.Search;
import de.eldoria.companies.commands.company.order.search.query.Clear;
import de.eldoria.companies.commands.company.order.search.query.Execute;
import de.eldoria.companies.commands.company.order.search.query.Material;
import de.eldoria.companies.commands.company.order.search.query.MaterialMatch;
import de.eldoria.companies.commands.company.order.search.query.MaterialSearch;
import de.eldoria.companies.commands.company.order.search.query.Name;
import de.eldoria.companies.commands.company.order.search.query.Order;
import de.eldoria.companies.commands.company.order.search.query.Price;
import de.eldoria.companies.commands.company.order.search.query.Render;
import de.eldoria.companies.commands.company.order.search.query.Size;
import de.eldoria.companies.commands.company.order.search.query.Sorting;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Query extends EldoCommand {
    private final Map<UUID, SearchQuery> searches = new HashMap<>();
    private final Render render;

    public Query(Plugin plugin, AOrderData orderData, Search search) {
        super(plugin);
        render = new Render(plugin, this);
        setDefaultCommand(render);
        registerCommand("clear", new Clear(plugin, this));
        registerCommand("execute", new Execute(plugin, this, search, orderData));
        var material = new Material(plugin, this);
        registerCommand("material_add", material);
        registerCommand("material_remove", material);
        registerCommand("material_match", new MaterialMatch(plugin, this));
        registerCommand("material_search", new MaterialSearch(plugin, this));
        registerCommand("name", new Name(plugin, this));
        registerCommand("order", new Order(plugin, this));
        var price = new Price(plugin, this);
        registerCommand("min_price", price);
        registerCommand("max_price", price);
        var size = new Size(plugin, this);
        registerCommand("min_size", size);
        registerCommand("max_size", size);
        registerCommand("sorting", new Sorting(plugin, this));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!super.onCommand(sender, command, label, args)) {
            return true;
        }
        render.renderSearch(getPlayerFromSender(sender));
        return true;
    }

    public SearchQuery getPlayerSearch(Player player) {
        return searches.computeIfAbsent(player.getUniqueId(), key -> new SearchQuery());
    }

    public void reset(Player player) {
        searches.put(player.getUniqueId(), new SearchQuery());
    }
}
