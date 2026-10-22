/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.data.wrapper.order.FullOrder;

import java.util.List;

/**
 * The orders a search found, with what the searching player's company needs to show them.
 *
 * @param orders       orders found
 * @param taxPercent   tax of the searching company, where 5 means 5 %
 * @param canAccept    whether the player may accept orders for the company
 * @param limitReached whether the company already works on as many orders as its level allows
 */
public record SearchResults(List<FullOrder> orders, double taxPercent, boolean canAccept, boolean limitReached) {
    /**
     * Results of a player who has not searched yet.
     *
     * @return empty results
     */
    public static SearchResults empty() {
        return new SearchResults(List.of(), 0.0, false, false);
    }
}
