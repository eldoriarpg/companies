/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.commands.company.order.search.Page;
import de.eldoria.companies.commands.company.order.search.Query;
import de.eldoria.companies.commands.company.order.search.SearchExecutor;
import de.eldoria.companies.commands.company.order.search.SearchResults;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Search extends AdvancedCommand {
    private final Page page;
    private final SearchExecutor executor;
    private final Map<UUID, SearchResults> results = new HashMap<>();
    private Query query;

    public Search(
            Plugin plugin,
            AOrderData orderData,
            ACompanyData companyData,
            Configuration configuration,
            Economy economy,
            MessageBlocker messageBlocker) {
        super(plugin);
        executor = new SearchExecutor(orderData, companyData, configuration);
        page = new Page(plugin, this, economy, messageBlocker);
        var meta = CommandMeta.builder("search")
                .buildSubCommands((commands, builder) -> {
                    query = new Query(plugin, this, messageBlocker);
                    commands.add(page);
                    commands.add(query);
                    builder.withDefaultCommand(query);
                })
                .build();
        meta(meta);
    }

    public Map<UUID, SearchResults> results() {
        return results;
    }

    public Page page() {
        return page;
    }

    public Query query() {
        return query;
    }

    /**
     * Runs the player's current search again and shows the given page of the fresh results. Has to be called on the
     * main thread.
     *
     * @param player player searching
     * @param page   page to show
     */
    public void showFresh(Player player, int page) {
        var optSearch = query.findPlayerSearch(player);
        if (optSearch.isEmpty()) {
            this.page.renderPage(player, page);
            return;
        }
        executor.execute(player, optSearch.get()).whenComplete(found -> {
            results.put(player.getUniqueId(), found);
            this.page.renderPage(player, page);
        });
    }
}
