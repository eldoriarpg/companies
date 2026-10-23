/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards.npc;

import org.bukkit.entity.Player;

/**
 * Told by the hooks when a player right-clicks an NPC.
 */
@FunctionalInterface
public interface NpcClickHandler {
    /**
     * @param player the player who clicked
     * @param hook   name of the NPC plugin
     * @param npcKey key of the NPC in that plugin
     */
    void clicked(Player player, String hook, String npcKey);
}
