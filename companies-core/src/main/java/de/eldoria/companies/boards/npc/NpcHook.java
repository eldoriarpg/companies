/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards.npc;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;

/**
 * Connects an NPC plugin to the boards. Implementations are compiled against the NPC plugin and only loaded by
 * {@link NpcHooks} while that plugin is enabled.
 */
public interface NpcHook {
    /**
     * @return name of the NPC plugin, like {@code Citizens}
     */
    String pluginName();

    /**
     * Registers the hook's listener, which reports right-clicks on NPCs to the handler.
     *
     * @param plugin  the plugin owning the listener
     * @param handler told about every right-click on an NPC
     */
    void enable(Plugin plugin, NpcClickHandler handler);

    /**
     * Unregisters the hook's listener.
     */
    void disable();

    /**
     * Resolves the NPC an admin names in a command.
     *
     * @param sender   the admin
     * @param argument the typed NPC: a Citizens id or {@code selected}, a FancyNpcs name
     * @return the NPC's key, empty when no such NPC exists
     */
    Optional<String> resolve(CommandSender sender, String argument);

    /**
     * @return the NPC arguments to suggest
     */
    List<String> suggestions();
}
