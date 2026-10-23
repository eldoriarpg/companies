/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards;

import de.eldoria.companies.commands.support.CommandFailure;
import org.bukkit.entity.Player;

/**
 * Opens boards for players: signs, NPCs and {@code /company board} end up here.
 */
public interface BoardOpener {
    /**
     * Opens the order search for the player, pre-filtered. Main thread.
     *
     * @param player player using the board
     * @param target what the board shows
     * @throws CommandFailure when the player may not use boards or the plugin is not ready
     */
    void open(Player player, BoardTarget target);
}
