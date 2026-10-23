/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

/**
 * What an order search is for: a company accepting orders, or a player working on them alone. Both share the query of
 * the player, so switching between them keeps the filter.
 *
 * @param queryCommand    command the chat form suggests for typed values, without trailing space
 * @param alerts          whether a search can be saved as an alert of the company
 * @param limitReachedKey locale key shown when the searcher may accept no more orders
 * @param offersSolo      whether the results offer to look for orders to work on alone
 */
public record SearchMode(String queryCommand, boolean alerts, String limitReachedKey, boolean offersSolo) {
    /**
     * The search of {@code /company order search}, boards and alerts.
     */
    public static final SearchMode COMPANY =
            new SearchMode("/company order search query", true, "company.order.search.limitReached", true);

    /**
     * The search of {@code /order work search}.
     */
    public static final SearchMode SOLO =
            new SearchMode("/order work search query", false, "solo.search.limitReached", false);

    /**
     * The command that runs the search of orders to work on alone with the current query.
     */
    public static final String SOLO_SEARCH = "order work search query execute";
}
