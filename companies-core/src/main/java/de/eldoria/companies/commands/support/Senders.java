/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.support;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Narrows the sender of a command.
 */
public final class Senders {
    private Senders() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @param sender sender of the command
     * @return the sender as player
     * @throws CommandFailure {@code error.onlyPlayer} when the sender is not a player
     */
    public static Player player(CommandSender sender) {
        if (sender instanceof Player player) return player;
        throw CommandFailure.of("error.onlyPlayer");
    }
}
