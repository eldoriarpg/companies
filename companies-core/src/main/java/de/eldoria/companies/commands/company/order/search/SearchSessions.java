/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The order searches of the players: the query each player builds and the results of the last search run.
 * <p>
 * Results arrive from a background thread and are read by suggestions, so both maps are concurrent.
 */
public final class SearchSessions {
    private final Map<UUID, SearchQuery> queries = new ConcurrentHashMap<>();
    private final Map<UUID, SearchResults> results = new ConcurrentHashMap<>();

    /**
     * @param player id of the player
     * @return the query of the player, created when the player has none
     */
    public SearchQuery query(UUID player) {
        return queries.computeIfAbsent(player, key -> new SearchQuery());
    }

    /**
     * @param player id of the player
     * @return the query of the player, empty when the player has none
     */
    public Optional<SearchQuery> findQuery(UUID player) {
        return Optional.ofNullable(queries.get(player));
    }

    /**
     * Replaces the query of a player with an empty one.
     *
     * @param player id of the player
     */
    public void reset(UUID player) {
        replace(player, new SearchQuery());
    }

    /**
     * Replaces the query of a player.
     *
     * @param player id of the player
     * @param query  the new query
     */
    public void replace(UUID player, SearchQuery query) {
        queries.put(player, query);
    }

    /**
     * @param player id of the player
     * @return the results of the last search, empty when the player has not searched
     */
    public SearchResults results(UUID player) {
        return results.getOrDefault(player, SearchResults.empty());
    }

    /**
     * @param player id of the player
     * @param found  results of the search the player ran
     */
    public void results(UUID player, SearchResults found) {
        results.put(player, found);
    }
}
