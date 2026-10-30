/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.data.wrapper.offer.OfferMark;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.orders.SystemOwner;
import de.eldoria.companies.orders.query.OrderFilter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The order search of one player: an editable {@link OrderFilter} plus sorting, the fillable switch, the event offer
 * filter and, while the server marks its own orders, which of them to show.
 */
public class SearchQuery {
    private String name;
    private List<String> materials = new ArrayList<>();
    private boolean anyMaterial = true;
    private boolean exactMatch;
    private double minPrice;
    private double maxPrice = Double.MAX_VALUE;
    private int minOrderSize;
    private int maxOrderSize = Integer.MAX_VALUE;
    private SortingType sortingType = SortingType.AGE;
    private boolean asc = true;
    private boolean fillable;
    private ServerOrders serverOrders = ServerOrders.ALL;
    private EventOffers eventOffers = EventOffers.ALL;
    private int offer;
    private int serverEvent;
    private boolean playersFirst;

    /**
     * A search with the given filter, sorted by age.
     *
     * @param filter filter to start from
     * @return new search
     */
    public static SearchQuery of(OrderFilter filter) {
        var query = new SearchQuery();
        query.name(filter.name());
        query.materials(new ArrayList<>(filter.materials()));
        query.anyMaterial(filter.anyMaterial());
        query.exactMatch(filter.exactMatch());
        query.minPrice = Math.max(0, filter.minPrice());
        query.maxPrice = Math.max(0, filter.maxPrice());
        query.minOrderSize = Math.max(0, filter.minSize());
        query.maxOrderSize = Math.max(0, filter.maxSize());
        return query;
    }

    /**
     * The filter part of this search as it is now.
     *
     * @return snapshot of the filter
     */
    public OrderFilter filter() {
        return new OrderFilter(
                name, materials, anyMaterial, exactMatch, minPrice, maxPrice, minOrderSize, maxOrderSize);
    }

    public void sort(List<FullOrder> orders) {
        sortingType().sort(orders, isAsc(), playersFirst);
    }

    /**
     * @return which orders of the server the search shows, while the server marks them
     */
    public ServerOrders serverOrders() {
        return serverOrders == null ? ServerOrders.ALL : serverOrders;
    }

    public void serverOrders(ServerOrders serverOrders) {
        this.serverOrders = serverOrders;
    }

    /**
     * Whether orders of players come before orders of the server that sort the same, set while the server marks its
     * orders.
     *
     * @param playersFirst true to list player orders first among equals
     */
    public void playersFirst(boolean playersFirst) {
        this.playersFirst = playersFirst;
    }

    /**
     * @return whether the search shows every order or only the chunks of event offers
     */
    public EventOffers eventOffers() {
        return eventOffers == null ? EventOffers.ALL : eventOffers;
    }

    /**
     * Shows every order or only the chunks of event offers, and forgets a chosen offer.
     *
     * @param eventOffers which orders to show
     */
    public void eventOffers(EventOffers eventOffers) {
        this.eventOffers = eventOffers;
        offer = 0;
    }

    /**
     * @return the event offer whose chunks alone the search shows, 0 for any
     */
    public int offer() {
        return offer;
    }

    /**
     * Shows only the chunks of one event offer.
     *
     * @param offer id of the offer, 0 to show every order again
     */
    public void offer(int offer) {
        this.offer = Math.max(0, offer);
        serverEvent = 0;
        eventOffers = this.offer > 0 ? EventOffers.ONLY : EventOffers.ALL;
    }

    /**
     * @return the server event whose offers' chunks alone are shown, 0 for any
     */
    public int serverEvent() {
        return serverEvent;
    }

    /**
     * Shows only the chunks of the offers of one server event.
     *
     * @param serverEvent id of the event, 0 for any
     */
    public void serverEvent(int serverEvent) {
        this.serverEvent = Math.max(0, serverEvent);
        offer = 0;
        eventOffers = this.serverEvent > 0 ? EventOffers.ONLY : EventOffers.ALL;
    }

    /**
     * @param mark the event offer an order belongs to, null when it belongs to none
     * @return whether the event offer filter shows the order
     */
    public boolean showsOffer(@Nullable OfferMark mark) {
        if (serverEvent > 0) return mark != null && Integer.valueOf(serverEvent).equals(mark.event());
        if (offer > 0) return mark != null && mark.offer() == offer;
        return eventOffers() == EventOffers.ALL || mark != null;
    }

    /**
     * Which orders a search shows by their event offers.
     */
    public enum EventOffers {
        /**
         * Every order.
         */
        ALL,
        /**
         * Only the chunks of event offers.
         */
        ONLY
    }

    /**
     * Which orders of the server a search shows.
     */
    public enum ServerOrders {
        /**
         * Orders of players and of the server.
         */
        ALL,
        /**
         * Only orders of the server.
         */
        ONLY,
        /**
         * Only orders of players.
         */
        HIDE;

        /**
         * @param order an order
         * @return true when the search shows it
         */
        public boolean shows(FullOrder order) {
            var server = SystemOwner.is(order.owner());
            return switch (this) {
                case ALL -> true;
                case ONLY -> server;
                case HIDE -> !server;
            };
        }
    }

    public SortingType sortingType() {
        return sortingType;
    }

    public boolean isAsc() {
        return asc;
    }

    public void name(@Nullable String name) {
        if (name != null) {
            this.name = name.substring(0, Math.min(name.length(), 32));
            return;
        }
        this.name = null;
    }

    public List<String> materials() {
        return materials;
    }

    public void materials(List<String> materials) {
        this.materials = materials;
    }

    public double minPrice() {
        return minPrice;
    }

    public void minPrice(double minPrice) {
        this.minPrice = Math.max(minPrice, 0.0);
        maxPrice = Math.max(this.minPrice, maxPrice);
    }

    public double maxPrice() {
        return maxPrice;
    }

    public void maxPrice(double maxPrice) {
        this.maxPrice = Math.max(maxPrice, 0.0);
        minPrice = Math.min(minPrice, this.maxPrice);
    }

    public int minOrderSize() {
        return minOrderSize;
    }

    public void minOrderSize(int minOrderSize) {
        this.minOrderSize = Math.max(minOrderSize, 0);
        maxOrderSize = Math.max(this.minOrderSize, maxOrderSize);
    }

    public int maxOrderSize() {
        return maxOrderSize;
    }

    public void maxOrderSize(int maxOrderSize) {
        this.maxOrderSize = Math.max(maxOrderSize, 0);
        minOrderSize = Math.min(minOrderSize, this.maxOrderSize);
    }

    public void sortingType(SortingType sortingType) {
        this.sortingType = sortingType;
    }

    public void asc(boolean asc) {
        this.asc = asc;
    }

    public String materialRegex() {
        return materialRegex("\\b");
    }

    /**
     * Builds a regex matching the space separated materials of an order.
     * <p>
     * When all materials are required, every material is checked with a lookahead, so the order of the materials does not matter.
     *
     * @param wordBoundary word boundary of the regex dialect, which is {@code \b} for most dialects and {@code \y} for PostgreSQL
     * @return regex for the materials of this query
     */
    public String materialRegex(String wordBoundary) {
        if (materials.isEmpty()) return ".*";
        if (anyMaterial) {
            return materials.stream()
                    .map(mat -> "(" + regexMat(mat, wordBoundary) + ")")
                    .collect(Collectors.joining("|"));
        }
        return materials.stream()
                .map(mat -> "(?=.*" + regexMat(mat, wordBoundary) + ")")
                .collect(Collectors.joining(""));
    }

    private String regexMat(String mat, String wordBoundary) {
        if (exactMatch) {
            return wordBoundary + mat + wordBoundary;
        }
        return mat;
    }

    public void anyMaterial(boolean anyMaterial) {
        this.anyMaterial = anyMaterial;
    }

    public void exactMatch(boolean exactMatch) {
        this.exactMatch = exactMatch;
    }

    public boolean isAnyMaterial() {
        return anyMaterial;
    }

    public boolean isExactMatch() {
        return exactMatch;
    }

    public String name() {
        return name == null ? "" : name;
    }

    /**
     * Whether only orders the searching player can deliver items to are shown.
     *
     * @return true when the search filters by the player's items
     */
    public boolean fillable() {
        return fillable;
    }

    public void fillable(boolean fillable) {
        this.fillable = fillable;
    }
}
