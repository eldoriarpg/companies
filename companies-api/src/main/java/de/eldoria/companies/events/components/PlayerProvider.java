/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.components;

import org.bukkit.OfflinePlayer;

/**
 * An event that involves a player.
 *
 * @param <P> type of player
 */
@FunctionalInterface
public interface PlayerProvider<P extends OfflinePlayer> {
    /**
     * The player involved in the event.
     *
     * @return player
     */
    P player();
}
