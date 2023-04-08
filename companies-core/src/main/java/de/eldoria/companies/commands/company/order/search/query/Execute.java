/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search.query;

import de.eldoria.companies.commands.company.order.Search;
import de.eldoria.companies.commands.company.order.search.Query;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class Execute extends AdvancedCommand implements IPlayerTabExecutor {
    private final Query query;
    private final AOrderData orderData;
    private final Search search;

    public Execute(Plugin plugin, Query query, Search search, AOrderData orderData) {
        super(plugin, CommandMeta.builder("execute")
                .build());
        this.query = query;
        this.orderData = orderData;
        this.search = search;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        orderData.retrieveOrdersByQuery(query.getPlayerSearch(player), OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                 .whenComplete(fullOrders -> {
                     search.results()
                           .put(player.getUniqueId(), fullOrders);
                     search.page()
                           .renderPage(player, 0);
                 });
    }
}
