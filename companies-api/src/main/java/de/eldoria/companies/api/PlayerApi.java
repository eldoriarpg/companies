/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Reads and changes the per-player data of Companies.
 * <p>
 * Every future completes off the main thread; see {@link CompaniesApi} for how to get back onto it.
 */
public interface PlayerApi {
    /**
     * Puts items into a player's returned items, which the player picks up with {@code /order collect}.
     * <p>
     * The items are stored in the database, so the player can pick them up on any server, also when they are offline
     * now. An {@link de.eldoria.companies.events.player.ItemsReturnedEvent} with the reason
     * {@link de.eldoria.companies.events.player.ItemsReturnedEvent.Reason#API API} is fired once they are stored.
     * <p>
     * This is a write operation: before Companies is ready the future completes exceptionally with an
     * {@link ApiNotReadyException}.
     *
     * @param player id of the player
     * @param stack  item to grant; its own amount is ignored and the stack is copied
     * @param amount how many of the item to grant, at least 1
     * @return future that completes once the items are stored
     * @throws IllegalArgumentException when the amount is below 1 or the stack is air
     */
    CompletableFuture<Void> grantItems(UUID player, ItemStack stack, int amount);

    /**
     * How many items wait for a player to pick them up with {@code /order collect}.
     *
     * @param player id of the player
     * @return future of the number of items, 0 when nothing is waiting
     */
    CompletableFuture<Integer> itemsWaiting(UUID player);
}
