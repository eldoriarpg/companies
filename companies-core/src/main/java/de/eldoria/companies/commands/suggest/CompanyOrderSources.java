/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import de.eldoria.companies.commands.company.order.search.SearchSessions;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Where the suggestions of {@code /company order} read their data from: the orders the company of a player works
 * on, cached like other database data, and the searches of the players, which are in memory.
 * <p>
 * The plugin hands the sources in when it enables and takes them back when it disables.
 */
public final class CompanyOrderSources {
    private static volatile Optional<CompanyOrderSources> current = Optional.empty();

    private final SuggestionCache<UUID, List<FullOrder>> companyOrders;
    private final SuggestionCache<UUID, List<FullOrder>> soloOrders;
    private final SearchSessions searches;

    /**
     * @param companyData company repository
     * @param orderData   order repository
     * @param searches    the order searches of the players
     * @param executor    executor loading data from the database
     */
    public CompanyOrderSources(
            ACompanyData companyData, AOrderData orderData, SearchSessions searches, Executor executor) {
        this(
                new SuggestionCache<>(
                        SuggestionSources.LIFETIME, executor, player -> claimedOrders(companyData, orderData, player)),
                new SuggestionCache<>(SuggestionSources.LIFETIME, executor, player -> soloOrders(orderData, player)),
                searches);
    }

    /**
     * Sources without solo orders.
     *
     * @param companyOrders cache of the orders the company of a player works on, by the player's id
     * @param searches      the order searches of the players
     */
    public CompanyOrderSources(SuggestionCache<UUID, List<FullOrder>> companyOrders, SearchSessions searches) {
        this(
                companyOrders,
                new SuggestionCache<>(SuggestionSources.LIFETIME, Runnable::run, player -> List.of()),
                searches);
    }

    /**
     * @param companyOrders cache of the orders the company of a player works on, by the player's id
     * @param soloOrders    cache of the orders a player works on alone, by the player's id
     * @param searches      the order searches of the players
     */
    public CompanyOrderSources(
            SuggestionCache<UUID, List<FullOrder>> companyOrders,
            SuggestionCache<UUID, List<FullOrder>> soloOrders,
            SearchSessions searches) {
        this.companyOrders = companyOrders;
        this.soloOrders = soloOrders;
        this.searches = searches;
    }

    /**
     * @param sources the sources the providers read from
     */
    public static void use(CompanyOrderSources sources) {
        current = Optional.of(sources);
    }

    /**
     * Stops suggesting data of the plugin.
     */
    public static void clear() {
        current = Optional.empty();
    }

    /**
     * @return the sources, empty while the plugin is disabled
     */
    public static Optional<CompanyOrderSources> current() {
        return current;
    }

    /**
     * Drops the cached orders of a player's company and of the player alone, after the player changed them.
     *
     * @param player id of the player
     */
    public static void invalidate(UUID player) {
        current.ifPresent(sources -> {
            sources.companyOrders().invalidate(player);
            sources.soloOrders().invalidate(player);
        });
    }

    /**
     * @return the orders the company of a player works on, by the player's id
     */
    public SuggestionCache<UUID, List<FullOrder>> companyOrders() {
        return companyOrders;
    }

    /**
     * @return the orders a player works on alone, by the player's id
     */
    public SuggestionCache<UUID, List<FullOrder>> soloOrders() {
        return soloOrders;
    }

    /**
     * @return the order searches of the players
     */
    public SearchSessions searches() {
        return searches;
    }

    private static List<FullOrder> soloOrders(AOrderData orderData, UUID player) {
        var orders = orderData
                .retrieveOrdersBySoloWorker(player, OrderState.CLAIMED, OrderState.CLAIMED)
                .asFuture()
                .join();
        if (orders == null) return List.of();
        return orderData.toFullOrders(orders);
    }

    private static List<FullOrder> claimedOrders(ACompanyData companyData, AOrderData orderData, UUID player) {
        var company = companyData
                .retrievePlayerCompany(Bukkit.getOfflinePlayer(player))
                .asFuture()
                .join();
        if (company == null || company.isEmpty()) return List.of();
        var orders = orderData
                .retrieveOrdersByCompany(company.get(), OrderState.CLAIMED, OrderState.CLAIMED)
                .asFuture()
                .join();
        if (orders == null) return List.of();
        return orderData.toFullOrders(orders);
    }
}
