/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.data.wrapper.order.FullOrder;

import java.util.List;

/**
 * The orders a search found, with what the searching player's company, or the player working alone, needs to show
 * them.
 *
 * @param orders        orders found
 * @param taxPercent    tax of the searching company or of solo orders, where 5 means 5 %
 * @param canAccept     whether the player may accept orders for the company, or alone
 * @param limitReached  whether the company or the player already works on as many orders as allowed
 * @param companyMember whether the player is a member of a company
 */
public record SearchResults(
        List<FullOrder> orders, double taxPercent, boolean canAccept, boolean limitReached, boolean companyMember) {
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
}
