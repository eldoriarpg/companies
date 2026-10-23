/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import de.eldoria.eldoutilities.threading.futures.BukkitFutureResult;
import org.bukkit.entity.Player;

/**
 * Runs a search and keeps only the orders the searching player could accept.
 */
@FunctionalInterface
public interface OrderFinder {
    /**
     * Runs the query. Has to be called on the main thread, since the fillable filter reads the player's inventory.
     *
     * @param player player searching
     * @param query  query to run
     * @return the orders the player could accept
     */
    BukkitFutureResult<SearchResults> execute(Player player, SearchQuery query);
}
