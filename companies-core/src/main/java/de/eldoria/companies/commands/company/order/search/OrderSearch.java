/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.menus.MenuService;
import de.eldoria.companies.menus.Page;
import de.eldoria.companies.menus.company.AlertNameMenu;
import de.eldoria.companies.menus.company.SearchMenu;
import de.eldoria.companies.menus.company.SearchResultsMenu;
import de.eldoria.companies.orders.query.OrderFilter;
import de.eldoria.companies.util.Permission;
import org.bukkit.Bukkit;
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
    private final SearchMode mode;
    private final OrderFinder executor;
    private final ObjIntConsumer<Player> info;
    private final ObjIntConsumer<Player> accept;

    /**
     * The search of a company.
     *
     * @param deps   what the commands need
     * @param info   shows a company order to a player
     * @param accept accepts an order for the company of a player
     */
    public OrderSearch(CommandDependencies deps, ObjIntConsumer<Player> info, ObjIntConsumer<Player> accept) {
        this(
                deps,
                SearchMode.COMPANY,
                new SearchExecutor(
                        deps.orderData(),
                        deps.companyData(),
                        deps.configuration(),
                        () -> deps.services().deliveryService().matcher(),
                        Bukkit::getOfflinePlayer,
                        orders -> deps.services().offerData().marks(orders),
                        deps::eventMarks),
                info,
                accept);
    }

    /**
     * A search of one mode.
     *
     * @param deps   what the commands need
     * @param mode   what the search is for
     * @param finder runs the searches
     * @param info   shows an order found to a player
     * @param accept accepts an order found for a player
     */
    public OrderSearch(
            CommandDependencies deps,
            SearchMode mode,
            OrderFinder finder,
            ObjIntConsumer<Player> info,
            ObjIntConsumer<Player> accept) {
        this.deps = deps;
        this.sessions = deps.searches();
        this.mode = mode;
        this.executor = finder;
        this.info = info;
        this.accept = accept;
    }

    /**
     * What this search is for.
     *
     * @return the mode
     */
    public SearchMode mode() {
        return mode;
    }

    /**
     * Whether the results offer the player to look for orders to work on alone: the search offers it, solo orders are
     * enabled, the player has the permission and is no company member, unless members may work alone as well.
     *
     * @param player  player searching
     * @param results what the search found
     * @return true when the button is shown
     */
    public boolean offersSolo(Player player, SearchResults results) {
        if (!mode.offersSolo()) return false;
        var solo = deps.configuration().orderSetting().solo();
        if (!solo.enabled() || !player.hasPermission(Permission.Orders.SOLO)) return false;
        return solo.forCompanyMembers() || !results.companyMember();
    }

    /**
     * Shows the orders the player could take alone, with the current query.
     *
     * @param player player searching
     */
    public void searchSolo(Player player) {
        player.performCommand(SearchMode.SOLO_SEARCH);
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
     * Narrows the player's search to the chunks of one event offer and shows the first page of results.
     *
     * @param player player searching
     * @param offer  id of the offer
     */
    public void showOffer(Player player, int offer) {
        query(player).offer(offer);
        showPage(player, 0);
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
        useForm(player, form, valid -> showPage(player, 0));
    }

    /**
     * Saves a filled in form as an alert. A valid form becomes the player's query and the name of the alert is asked
     * for; otherwise the form is shown again, as filled in, with the invalid input named.
     *
     * @param player player searching
     * @param form   the filled in form
     */
    public void saveAlert(Player player, SearchForm form) {
        useForm(player, form, valid -> menus().show(
                        player, new AlertNameMenu(deps.messages(), this::showForm), new AlertNameMenu.Model("")));
    }

    /**
     * Shows the orders a saved filter finds: the filter replaces the player's query, sorted by age. Has to be called
     * on the main thread, like {@link #showPage(Player, int)}.
     *
     * @param player player searching
     * @param filter the saved filter
     */
    public void showFilter(Player player, OrderFilter filter) {
        deps.requireReady();
        sessions.replace(player.getUniqueId(), SearchQuery.of(filter));
        showPage(player, 0);
    }

    private void useForm(Player player, SearchForm form, Consumer<SearchQuery> then) {
        deps.requireReady();
        switch (form.toQuery()) {
            case SearchForm.Valid valid -> {
                sessions.replace(player.getUniqueId(), valid.query());
                then.accept(valid.query());
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
            menus().show(player, resultsMenu(), new SearchResultsMenu.Model(found, shown, offersSolo(player, found)));
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
     * Accepts an order found by the search for the player's company, or for the player alone.
     *
     * @param player player searching
     * @param id     id of the order
     */
    public void accept(Player player, int id) {
        accept.accept(player, id);
    }

    /**
     * @return whether the server marks its own orders, so searches offer to show or hide them and results mark them
     */
    public boolean marksServerOrders() {
        var configuration = deps.configuration();
        return configuration != null && configuration.systemOrderSettings().markSystemOrders();
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
