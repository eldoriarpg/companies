/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.data.wrapper.order.FullOrder;

import java.util.List;

/**
 * The orders a search found, with the tax of the searching player's company for showing what the company would earn.
 *
 * @param orders     orders found
 * @param taxPercent tax of the searching company, where 5 means 5 %
 */
public record SearchResults(List<FullOrder> orders, double taxPercent) {
    /**
     * Results of a player who has not searched yet.
     *
     * @return empty results
     */
    public static SearchResults empty() {
        return new SearchResults(List.of(), 0.0);
    }
}
