/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards;

import de.eldoria.companies.commands.company.order.search.SearchQuery;
import de.eldoria.companies.orders.query.OrderFilter;
import org.jetbrains.annotations.Nullable;

/**
 * What a board shows: a preset of the configuration, a list of materials, the chunks of event offers or every open
 * order.
 *
 * @param preset name of the preset in lower case, or null when the board is not a preset
 * @param filter the orders shown
 * @param label  name of the board shown on signs, blank for every open order
 * @param offers the event offers whose chunks alone the board shows: 0 for every offer, an id for one, null for any
 *               order
 */
public record BoardTarget(
        @Nullable String preset,
        OrderFilter filter,
        String label,
        @Nullable Integer offers) {
    /**
     * The board of every open order.
     */
    public static final BoardTarget ALL = new BoardTarget(null, OrderFilter.ALL, "");

    /**
     * The word that makes a board show the chunks of event offers: {@code event} for all of them, {@code event <id>}
     * for one.
     */
    public static final String EVENT = "event";

    /**
     * A board of any order.
     *
     * @param preset name of the preset in lower case, or null when the board is not a preset
     * @param filter the orders shown
     * @param label  name of the board shown on signs, blank for every open order
     */
    public BoardTarget(@Nullable String preset, OrderFilter filter, String label) {
        this(preset, filter, label, null);
    }

    /**
     * The text a board is stored and resolved by again: the preset name, {@code event} with the offer's id when it
     * shows one offer, the materials separated by spaces, or an empty text for every open order.
     *
     * @return the stored text of the board
     */
    public String spec() {
        if (preset != null) return preset;
        if (offers != null) return offers == 0 ? EVENT : EVENT + " " + offers;
        return String.join(" ", filter.materials());
    }

    /**
     * The board as a search: its filter, and the event offer filter of a board of event offers.
     *
     * @return a new query
     */
    public SearchQuery query() {
        var query = SearchQuery.of(filter);
        if (offers != null) {
            if (offers == 0) query.eventOffers(SearchQuery.EventOffers.ONLY);
            else query.offer(offers);
        }
        return query;
    }
}
