package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.commands.company.order.search.All;
import de.eldoria.companies.commands.company.order.search.MaterialSearch;
import de.eldoria.companies.commands.company.order.search.NameSearch;
import de.eldoria.companies.commands.company.order.search.Page;
import de.eldoria.companies.data.OrderData;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Search extends EldoCommand {
    private final Page page;
    private Map<UUID, List<FullOrder>> results = new HashMap<>();

    public Search(Plugin plugin, OrderData orderData, Economy economy) {
        super(plugin);
        page = new Page(plugin, this, economy);
        registerCommand("all", new All(plugin, orderData, this));
        registerCommand("material", new MaterialSearch(plugin, orderData, this));
        registerCommand("name", new NameSearch(plugin, orderData, this));
        registerCommand("page", page);
    }

    public Map<UUID, List<FullOrder>> results() {
        return results;
    }

    public Page page() {
        return page;
    }
}
