package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.commands.company.order.search.Page;
import de.eldoria.companies.commands.company.order.search.Query;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.companies.services.messages.IMessageBlockerService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Search extends AdvancedCommand {
    private final Page page;
    private final Map<UUID, List<FullOrder>> results = new HashMap<>();

    public Search(Plugin plugin, AOrderData orderData, Economy economy, IMessageBlockerService messageBlocker) {
        super(plugin);
        page = new Page(plugin, this, economy);
        var meta = CommandMeta.builder("search")
                .buildSubCommands((commands, builder) -> {
                    var query = new Query(plugin, orderData, this, messageBlocker);
                    commands.add(page);
                    commands.add(query);
                    builder.withDefaultCommand(query);
                }).build();
        meta(meta);
    }

    public Map<UUID, List<FullOrder>> results() {
        return results;
    }

    public Page page() {
        return page;
    }
}
