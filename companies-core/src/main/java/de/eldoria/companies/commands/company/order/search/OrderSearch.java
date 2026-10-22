/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.menus.MenuService;
import de.eldoria.companies.menus.Page;
import de.eldoria.companies.menus.company.SearchMenu;
import de.eldoria.companies.menus.company.SearchResultsMenu;
import org.bukkit.entity.Player;

import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

/**
 * The order search of the players: shows the search form and the results, and runs the searches.
 * <p>
 * Every page of results shown runs the search again, so the results are never stale. Both the commands and the
 * buttons of the search menus end up here.
 */
public final class OrderSearch {
    /**
     * Orders shown per page of results.
     */
    public static final int PAGE_SIZE = 10;

    private final CommandDependencies deps;
    private final SearchSessions sessions;
    private final SearchExecutor executor;
    private final ObjIntConsumer<Player> info;
    private final ObjIntConsumer<Player> accept;

    /**
     * @param deps   what the commands need
     * @param info   shows a company order to a player
     * @param accept accepts an order for the company of a player
     */
    public OrderSearch(CommandDependencies deps, ObjIntConsumer<Player> info, ObjIntConsumer<Player> accept) {
        this.deps = deps;
        this.sessions = deps.searches();
        this.executor =
                new SearchExecutor(deps.orderData(), deps.companyData(), deps.configuration(), () -> deps.services()
                        .deliveryService()
                        .matcher());
        this.info = info;
        this.accept = accept;
    }

    /**
     * The search query of a player, created when missing. Refuses while the services do not exist.
     *
     * @param player player searching
     * @return the query
     */
    public SearchQuery query(Player player) {
        deps.requireReady();
        return sessions.query(player.getUniqueId());
    }

    /**
     * Shows the search form with the player's query.
     *
     * @param player player searching
     */
    public void showForm(Player player) {
        menus().show(player, searchMenu(), SearchMenu.Model.of(query(player)));
    }

    /**
     * Changes the player's query and shows the form again.
     *
     * @param player player searching
     * @param change change of the query
     */
    public void change(Player player, Consumer<SearchQuery> change) {
        change.accept(query(player));
        showForm(player);
    }

    /**
     * Replaces the player's query with an empty one and shows the form again.
     *
     * @param player player searching
     */
    public void reset(Player player) {
        deps.requireReady();
        sessions.reset(player.getUniqueId());
        showForm(player);
    }

    /**
     * Searches with a filled in form. A valid form becomes the player's query and its first page of results is shown;
     * otherwise the form is shown again, as filled in, with the invalid input named.
     *
     * @param player player searching
     * @param form   the filled in form
     */
    public void submit(Player player, SearchForm form) {
        deps.requireReady();
        switch (form.toQuery()) {
            case SearchForm.Valid valid -> {
                sessions.replace(player.getUniqueId(), valid.query());
                showPage(player, 0);
            }
            case SearchForm.Invalid invalid ->
                menus().show(player, searchMenu(), SearchMenu.Model.invalid(query(player), form, invalid.input()));
        }
    }

    /**
     * Runs the player's search again and shows a page of the fresh results. Has to be called on the main thread, since
     * the fillable filter reads the player's inventory.
     *
     * @param player player searching
     * @param page   page to show, from zero
     */
    public void showPage(Player player, int page) {
        var query = query(player);
        executor.execute(player, query).whenComplete(found -> {
            sessions.results(player.getUniqueId(), found);
            var shown = Page.of(found.orders(), page, PAGE_SIZE);
            menus().show(player, resultsMenu(), new SearchResultsMenu.Model(found, shown));
        });
    }

    /**
     * Shows an order found by the search.
     *
     * @param player player searching
     * @param id     id of the order
     */
    public void info(Player player, int id) {
        info.accept(player, id);
    }

    /**
     * Accepts an order found by the search for the player's company.
     *
     * @param player player searching
     * @param id     id of the order
     */
    public void accept(Player player, int id) {
        accept.accept(player, id);
    }

    private SearchMenu searchMenu() {
        return new SearchMenu(this, deps.messages());
    }

    private SearchResultsMenu resultsMenu() {
        return new SearchResultsMenu(this, deps.messages(), deps.services().economy());
    }

    private MenuService menus() {
        return deps.services().menus();
    }
}
