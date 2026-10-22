/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.orders.Deliverable;
import de.eldoria.companies.orders.ItemMatcher;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.threading.futures.BukkitFutureResult;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Runs a search for a player and keeps only the orders the player's company could accept.
 * <p>
 * Hidden are orders of the company's own members (unless allowed), orders larger than the company's level allows and,
 * with the fillable filter, orders the player can deliver nothing to. A company at its order limit gets no orders.
 */
public class SearchExecutor {
    private final AOrderData orderData;
    private final ACompanyData companyData;
    private final Configuration configuration;
    private final Supplier<ItemMatcher> matcher;

    /**
     * @param orderData     order repository
     * @param companyData   company repository
     * @param configuration configuration of the plugin
     * @param matcher       the current item matcher, which decides what the player can deliver
     */
    public SearchExecutor(
            AOrderData orderData,
            ACompanyData companyData,
            Configuration configuration,
            Supplier<ItemMatcher> matcher) {
        this.orderData = orderData;
        this.companyData = companyData;
        this.configuration = configuration;
        this.matcher = matcher;
    }

    /**
     * Runs the query. Has to be called on the main thread, since the fillable filter reads the player's inventory.
     *
     * @param player player searching
     * @param query  query to run
     * @return the orders the player's company could accept
     */
    public BukkitFutureResult<SearchResults> execute(Player player, SearchQuery query) {
        List<ItemStack> available = query.fillable() ? Deliverable.available(player) : List.of();
        var itemMatcher = matcher.get();
        var orders = orderData
                .retrieveOrdersByQuery(query, OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                .asFuture();
        return BukkitFutureResult.of(companyData
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .thenCombineAsync(
                        orders,
                        (company, found) -> results(player, company, filled(found, query, itemMatcher, available)),
                        Threading.VIRTUAL));
    }

    private static List<FullOrder> filled(
            List<FullOrder> orders, SearchQuery query, ItemMatcher matcher, List<ItemStack> available) {
        if (!query.fillable()) return orders;
        var fillable = new ArrayList<>(orders.stream()
                .filter(order -> Deliverable.amount(matcher, available, order) > 0)
                .toList());
        fillable.sort(Comparator.comparingInt((FullOrder order) -> Deliverable.amount(matcher, available, order))
                .reversed());
        return fillable;
    }

    private SearchResults results(Player player, Optional<CompanyProfile> optCompany, List<FullOrder> orders) {
        var settings = configuration.companySettings();
        if (optCompany.isEmpty())
            return new SearchResults(orders, settings.tax().percent(), false, false);
        var company = optCompany.get();
        var taxPercent = settings.taxPercent(company.level());
        var canAccept = company.member(player)
                .map(member -> member.hasPermission(CompanyPermission.MANAGE_ORDERS))
                .orElse(false);
        var level = settings.levelSettings(company.level());
        var accepted = orderData.retrieveCompanyOrderCount(company).join();
        if (accepted != null && accepted >= level.maxOrders()) {
            return new SearchResults(List.of(), taxPercent, canAccept, true);
        }
        var acceptable = orders.stream()
                .filter(order -> company.mayAcceptOrderOf(order.owner(), settings.allowMemberOrders()))
                .filter(order -> !level.exceedsOrderSize(order.amount()))
                .toList();
        return new SearchResults(acceptable, taxPercent, canAccept, false);
    }
}
