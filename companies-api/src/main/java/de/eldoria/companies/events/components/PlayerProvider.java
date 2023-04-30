/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.components;

import org.bukkit.OfflinePlayer;

@FunctionalInterface
public interface PlayerProvider<P extends OfflinePlayer> {
    P player();
}
