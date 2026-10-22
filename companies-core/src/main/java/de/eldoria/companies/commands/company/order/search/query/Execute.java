/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search.query;

import de.eldoria.companies.commands.company.order.Search;
import de.eldoria.companies.commands.company.order.search.Query;
import de.eldoria.companies.commands.company.order.search.SearchResults;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.threading.futures.BukkitFutureResult;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Execute extends AdvancedCommand implements IPlayerTabExecutor {
    private final Query query;
    private final AOrderData orderData;
    private final ACompanyData companyData;
    private final Configuration configuration;
    private final Search search;

    public Execute(
            Plugin plugin,
            Query query,
            Search search,
            AOrderData orderData,
            ACompanyData companyData,
            Configuration configuration) {
        super(plugin, CommandMeta.builder("execute").build());
        this.query = query;
        this.orderData = orderData;
        this.companyData = companyData;
        this.configuration = configuration;
        this.search = search;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments)
            throws CommandException {
        var orders = orderData
                .retrieveOrdersByQuery(query.getPlayerSearch(player), OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                .asFuture();
        BukkitFutureResult.of(companyData
                        .retrievePlayerCompanyProfile(player)
                        .asFuture()
                        .thenCombine(orders, this::acceptableOrders))
                .whenComplete(fullOrders -> {
                    search.results().put(player.getUniqueId(), fullOrders);
                    search.page().renderPage(player, 0);
                });
    }

    private SearchResults acceptableOrders(Optional<CompanyProfile> company, List<FullOrder> orders) {
        var settings = configuration.companySettings();
        if (company.isEmpty()) return new SearchResults(orders, settings.tax().percent());
        var allowMemberOrders = settings.allowMemberOrders();
        var acceptable = orders.stream()
                .filter(order -> company.get().mayAcceptOrderOf(order.owner(), allowMemberOrders))
                .collect(Collectors.toList());
        return new SearchResults(acceptable, settings.taxPercent(company.get().level()));
    }
}
