/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.offer.OfferMark;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.orders.Deliverable;
import de.eldoria.companies.orders.ItemMatcher;
import de.eldoria.companies.orders.offers.OfferMarks;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.threading.futures.BukkitFutureResult;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Runs a search for a player and keeps only the orders the player's company could accept.
 * <p>
 * Hidden are orders of the company's own members (unless allowed), orders larger than the company's level allows and,
 * with the fillable filter, orders the player can deliver nothing to. A company at its order limit gets no orders.
 * <p>
 * The fillable filter reads the inventory of an online player. A search for a player given by id, who does not need
 * to be online, ignores it.
 * <p>
 * The chunks of event offers are always marked with their offer, whether the server marks its own orders or not; the
 * event offer filter keeps only them, or only those of one offer, and they may be sorted by their offer's end. The
 * choice of server orders does not hide them.
 */
public class SearchExecutor implements OrderFinder {
    private final AOrderData orderData;
    private final ACompanyData companyData;
    private final Configuration configuration;
    private final Supplier<ItemMatcher> matcher;
    private final Function<UUID, OfflinePlayer> players;
    private final OfferMarks offers;
    private final EventMarks events;

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
        this(orderData, companyData, configuration, matcher, OfferMarks.NONE);
    }

    /**
     * @param orderData     order repository
     * @param companyData   company repository
     * @param configuration configuration of the plugin
     * @param matcher       the current item matcher, which decides what the player can deliver
     * @param offers        finds the event offers of the found orders
     */
    public SearchExecutor(
            AOrderData orderData,
            ACompanyData companyData,
            Configuration configuration,
            Supplier<ItemMatcher> matcher,
            OfferMarks offers) {
        this(orderData, companyData, configuration, matcher, Bukkit::getOfflinePlayer, offers);
    }

    /**
     * @param orderData     order repository
     * @param companyData   company repository
     * @param configuration configuration of the plugin
     * @param matcher       the current item matcher, which decides what the player can deliver
     * @param players       looks up a player by id
     */
    public SearchExecutor(
            AOrderData orderData,
            ACompanyData companyData,
            Configuration configuration,
            Supplier<ItemMatcher> matcher,
            Function<UUID, OfflinePlayer> players) {
        this(orderData, companyData, configuration, matcher, players, OfferMarks.NONE);
    }

    /**
     * @param orderData     order repository
     * @param companyData   company repository
     * @param configuration configuration of the plugin
     * @param matcher       the current item matcher, which decides what the player can deliver
     * @param players       looks up a player by id
     * @param offers        finds the event offers of the found orders
     */
    public SearchExecutor(
            AOrderData orderData,
            ACompanyData companyData,
            Configuration configuration,
            Supplier<ItemMatcher> matcher,
            Function<UUID, OfflinePlayer> players,
            OfferMarks offers) {
        this(orderData, companyData, configuration, matcher, players, offers, EventMarks.NONE);
    }

    /**
     * @param orderData     order repository
     * @param companyData   company repository
     * @param configuration configuration of the plugin
     * @param matcher       the current item matcher, which decides what the player can deliver
     * @param players       looks up a player by id
     * @param offers        finds the event offers of the found orders, which are always marked
     * @param events        finds what the server events running now do to the found orders
     */
    public SearchExecutor(
            AOrderData orderData,
            ACompanyData companyData,
            Configuration configuration,
            Supplier<ItemMatcher> matcher,
            Function<UUID, OfflinePlayer> players,
            OfferMarks offers,
            EventMarks events) {
        this.events = events;
        this.orderData = orderData;
        this.companyData = companyData;
        this.configuration = configuration;
        this.matcher = matcher;
        this.players = players;
        this.offers = offers;
    }

    /**
     * Runs the query. Has to be called on the main thread, since the fillable filter reads the player's inventory.
     *
     * @param player player searching
     * @param query  query to run
     * @return the orders the player's company could accept
     */
    @Override
    public BukkitFutureResult<SearchResults> execute(Player player, SearchQuery query) {
        List<ItemStack> available = query.fillable() ? Deliverable.available(player) : List.of();
        return BukkitFutureResult.of(run(player, query, query.fillable(), available));
    }

    /**
     * Runs the query for a player who does not need to be online. The fillable filter of the query is ignored.
     *
     * @param player id of the player searching
     * @param query  query to run
     * @return the orders the player's company could accept
     */
    public CompletableFuture<SearchResults> search(UUID player, SearchQuery query) {
        return run(players.apply(player), query, false, List.of());
    }

    private CompletableFuture<SearchResults> run(
            OfflinePlayer player, SearchQuery query, boolean fillable, List<ItemStack> available) {
        var itemMatcher = matcher.get();
        var marked = configuration.systemOrderSettings().markSystemOrders();
        query.playersFirst(marked);
        var orders = orderData
                .retrieveOrdersByQuery(query, OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                .asFuture();
        return companyData
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .thenCombineAsync(
                        orders,
                        (company, found) -> {
                            var listed = fillable ? filled(found, query, itemMatcher, available) : found;
                            var marks = marks(offers, listed);
                            var results = results(player.getUniqueId(), company, shown(listed, query, marked, marks))
                                    .withOffers(marks);
                            var level = company.map(profile -> OptionalInt.of(profile.level()))
                                    .orElse(OptionalInt.empty());
                            return results.withEvents(events.marks(results.orders(), level));
                        },
                        Threading.VIRTUAL);
    }

    /**
     * Keeps the orders of the server the query asks for, while the server marks them.
     *
     * @param orders orders found
     * @param query  the query
     * @param marked whether the server marks its orders
     * @return the orders shown
     */
    static List<FullOrder> shown(List<FullOrder> orders, SearchQuery query, boolean marked) {
        return shown(orders, query, marked, Map.of());
    }

    /**
     * Keeps the orders the query asks for: by their event offers, and the orders of the server while the server marks
     * them, never hiding the chunks of event offers for being the server's. Sorted by the end of their offers when the
     * query asks for it.
     *
     * @param orders orders found
     * @param query  the query
     * @param marked whether the server marks its orders
     * @param marks  the event offer of each order that belongs to one, by order id
     * @return the orders shown
     */
    static List<FullOrder> shown(
            List<FullOrder> orders, SearchQuery query, boolean marked, Map<Integer, OfferMark> marks) {
        var shown = new ArrayList<>(orders.stream()
                .filter(order -> query.showsOffer(marks.get(order.id())))
                .filter(order -> !marked
                        || marks.containsKey(order.id())
                        || query.serverOrders().shows(order))
                .toList());
        if (query.sortingType() == SortingType.OFFER_END) SortingType.byOfferEnd(shown, marks, query.isAsc());
        return shown;
    }

    /**
     * @param offers finds the event offers of orders
     * @param orders the orders
     * @return the event offer of each order that belongs to one, by order id
     */
    static Map<Integer, OfferMark> marks(OfferMarks offers, List<FullOrder> orders) {
        if (orders.isEmpty()) return Map.of();
        return offers.marks(orders.stream().map(FullOrder::id).toList());
    }

    /**
     * Keeps the orders the player can deliver to when the query asks for it, the most deliverable first.
     *
     * @param orders    orders found
     * @param query     the query
     * @param matcher   decides what the player can deliver
     * @param available the items the player carries
     * @return the orders, unchanged without the fillable filter
     */
    static List<FullOrder> filled(
            List<FullOrder> orders, SearchQuery query, ItemMatcher matcher, List<ItemStack> available) {
        if (!query.fillable()) return orders;
        Map<FullOrder, Integer> amounts = new IdentityHashMap<>();
        for (var order : orders) {
            var amount = Deliverable.amount(matcher, available, order);
            if (amount > 0) amounts.put(order, amount);
        }
        var fillable =
                new ArrayList<>(orders.stream().filter(amounts::containsKey).toList());
        fillable.sort(Comparator.comparingInt((FullOrder order) -> amounts.getOrDefault(order, 0))
                .reversed());
        return fillable;
    }

    private SearchResults results(UUID player, Optional<CompanyProfile> optCompany, List<FullOrder> orders) {
        var settings = configuration.companySettings();
        if (optCompany.isEmpty())
            return new SearchResults(orders, settings.tax().percent(), false, false);
        var company = optCompany.get();
        var taxPercent = settings.taxPercent(company.level());
        var canAccept = company.member(player)
                .map(member -> member.hasPermission(CompanyPermission.MANAGE_ORDERS))
                .orElse(false);
        var level = settings.levelSettings(company.level());
        var accepted = orderData.retrieveCompanySlotCount(company).join();
        if (accepted != null && accepted >= level.maxOrders()) {
            return new SearchResults(List.of(), taxPercent, canAccept, true, true);
        }
        var acceptable = orders.stream()
                .filter(order -> company.mayAcceptOrderOf(order.owner(), settings.allowMemberOrders()))
                .filter(order -> !level.exceedsOrderSize(order.amount()))
                .toList();
        return new SearchResults(acceptable, taxPercent, canAccept, false, true);
    }
}
