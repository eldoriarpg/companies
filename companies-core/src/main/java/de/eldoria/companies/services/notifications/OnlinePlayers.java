/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The players online on this node, as far as notifications are concerned. Read on the main thread.
 */
public interface OnlinePlayers {
    /**
     * @param player id of the player
     * @return the player when online here, otherwise null
     */
    @Nullable
    Player find(UUID player);

    /**
     * @return ids of every player online here
     */
    List<UUID> ids();

    /**
     * The players online on a server.
     *
     * @param server the server
     * @return its online players
     */
    static OnlinePlayers of(Server server) {
        return new OnlinePlayers() {
            @Override
            public @Nullable Player find(UUID player) {
                return server.getPlayer(player);
            }

            @Override
            public List<UUID> ids() {
                return server.getOnlinePlayers().stream()
                        .map(Player::getUniqueId)
                        .toList();
            }
        };
    }
}
