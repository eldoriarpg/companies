/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.data.wrapper.offer.OfferMark;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.services.events.EventPreviews.EventMark;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The orders a search found, with what the searching player's company, or the player working alone, needs to show
 * them.
 *
 * @param orders        orders found
 * @param taxPercent    tax of the searching company or of solo orders, where 5 means 5 %
 * @param canAccept     whether the player may accept orders for the company, or alone
 * @param limitReached  whether the company or the player already works on as many orders as allowed
 * @param companyMember whether the player is a member of a company
 * @param offers        the event offer of each found order that belongs to one, by order id
 * @param events        what the server events running now do to each found order they boost, by order id
 */
public record SearchResults(
        List<FullOrder> orders,
        double taxPercent,
        boolean canAccept,
        boolean limitReached,
        boolean companyMember,
        Map<Integer, OfferMark> offers,
        Map<Integer, EventMark> events) {
    /**
     * Results without server events.
     *
     * @param orders        orders found
     * @param taxPercent    tax of the searching company or of solo orders, where 5 means 5 %
     * @param canAccept     whether the player may accept orders for the company, or alone
     * @param limitReached  whether the company or the player already works on as many orders as allowed
     * @param companyMember whether the player is a member of a company
     * @param offers        the event offer of each found order that belongs to one, by order id
     */
    public SearchResults(
            List<FullOrder> orders,
            double taxPercent,
            boolean canAccept,
            boolean limitReached,
            boolean companyMember,
            Map<Integer, OfferMark> offers) {
        this(orders, taxPercent, canAccept, limitReached, companyMember, offers, Map.of());
    }

    /**
     * Results without event offers.
     *
     * @param orders        orders found
     * @param taxPercent    tax of the searching company or of solo orders, where 5 means 5 %
     * @param canAccept     whether the player may accept orders for the company, or alone
     * @param limitReached  whether the company or the player already works on as many orders as allowed
     * @param companyMember whether the player is a member of a company
     */
    public SearchResults(
            List<FullOrder> orders, double taxPercent, boolean canAccept, boolean limitReached, boolean companyMember) {
        this(orders, taxPercent, canAccept, limitReached, companyMember, Map.of());
    }

    /**
     * Results of a player outside of any company.
     *
     * @param orders       orders found
     * @param taxPercent   tax shown with the orders, where 5 means 5 %
     * @param canAccept    whether the player may accept the orders
     * @param limitReached whether the player may accept no more orders
     */
    public SearchResults(List<FullOrder> orders, double taxPercent, boolean canAccept, boolean limitReached) {
        this(orders, taxPercent, canAccept, limitReached, false);
    }

    /**
     * Results of a player who has not searched yet.
     *
     * @return empty results
     */
    public static SearchResults empty() {
        return new SearchResults(List.of(), 0.0, false, false);
    }

    /**
     * @param marks the event offers of the found orders, by order id
     * @return these results with the event offers of the orders they hold
     */
    public SearchResults withOffers(Map<Integer, OfferMark> marks) {
        return new SearchResults(orders, taxPercent, canAccept, limitReached, companyMember, Map.copyOf(marks), events);
    }

    /**
     * @param marks what the server events do to the found orders, by order id
     * @return these results with the marks of the orders they hold
     */
    public SearchResults withEvents(Map<Integer, EventMark> marks) {
        return new SearchResults(orders, taxPercent, canAccept, limitReached, companyMember, offers, Map.copyOf(marks));
    }

    /**
     * @param order id of a found order
     * @return what the server events running now do to it, empty when none boosts it
     */
    public Optional<EventMark> event(int order) {
        return Optional.ofNullable(events.get(order));
    }

    /**
     * @param order id of a found order
     * @return the event offer the order belongs to, empty when it belongs to none
     */
    public Optional<OfferMark> offer(int order) {
        return Optional.ofNullable(offers.get(order));
    }
}
