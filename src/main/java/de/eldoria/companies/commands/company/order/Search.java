package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.commands.company.order.search.All;
import de.eldoria.companies.commands.company.order.search.MaterialSearch;
import de.eldoria.companies.commands.company.order.search.NameSearch;
import de.eldoria.companies.commands.company.order.search.Page;
import de.eldoria.companies.data.CompanyData;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Search extends EldoCommand {
    private CompanyData companyData;
    private Map<UUID, List<FullOrder>> results = new HashMap<>();
    private final Page page;

    public Search(Plugin plugin) {
        super(plugin);
        page = new Page(plugin, this);
        registerCommand("material", new MaterialSearch(plugin, this));
        registerCommand("name", new NameSearch(plugin, this));
        registerCommand("page", page);
        registerCommand("all", new All(plugin, this));
    }

    public Map<UUID, List<FullOrder>> results() {
        return results;
    }

    public Page page() {
        return page;
    }
}
