/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Function;

/**
 * Names of players by their id. The server names only players who joined it; a player who never did is looked up in
 * the server's user cache, which knows players from earlier lookups by name and from its {@code usercache.json}.
 */
public final class PlayerNames {
    private PlayerNames() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @param player the player
     * @return the name, null when neither the server nor its user cache knows it
     */
    public static @Nullable String of(OfflinePlayer player) {
        return of(player, Bukkit::createProfile);
    }

    /**
     * @param player   the player
     * @param profiles creates an empty profile of a player id
     * @return the name, null when neither the server nor its user cache knows it
     */
    static @Nullable String of(OfflinePlayer player, Function<UUID, PlayerProfile> profiles) {
        var name = player.getName();
        if (name != null) return name;
        var profile = profiles.apply(player.getUniqueId());
        profile.completeFromCache();
        return profile.getName();
    }

    /**
     * @param id the player's id
     * @return the name, null when neither the server nor its user cache knows it
     */
    public static @Nullable String of(UUID id) {
        return of(Bukkit.getOfflinePlayer(id));
    }
}
