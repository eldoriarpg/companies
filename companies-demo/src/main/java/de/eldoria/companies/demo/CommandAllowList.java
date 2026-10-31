/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lets visitors use only the commands of the demo: the plugin's and Companies' own, and the few of the land and
 * economy plugins a visitor needs. Others are refused and left out of tab completion; {@value #ALL} lifts the list.
 * A command with a plugin prefix, such as {@code minecraft:give}, is allowed only when the list names it with that
 * prefix, so another plugin's command of a listed name stays out.
 */
final class CommandAllowList implements Listener {
    /**
     * Uses every command.
     */
    static final String ALL = "companiesdemo.commands.all";

    private final Set<String> allowed;
    private final String refused;

    /**
     * @param allowed the commands visitors may use, without slash
     * @param refused the message a refused command answers with
     */
    CommandAllowList(Set<String> allowed, String refused) {
        this.allowed = allowed.stream().map(CommandAllowList::label).collect(Collectors.toUnmodifiableSet());
        this.refused = refused;
    }

    /**
     * @param command a command as typed, with or without slash and arguments, possibly with a plugin prefix such as
     *                {@code minecraft:give}
     * @return whether a visitor may use it
     */
    boolean allows(String command) {
        var text = command.startsWith("/") ? command.substring(1) : command;
        var end = text.indexOf(' ');
        return allowed.contains(label(end < 0 ? text : text.substring(0, end)));
    }

    private static String label(String command) {
        return command.toLowerCase(Locale.ROOT);
    }

    /**
     * Refuses a command outside the list.
     *
     * @param event the command a player typed
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.getPlayer().hasPermission(ALL) || allows(event.getMessage())) return;
        event.setCancelled(true);
        event.getPlayer().sendRichMessage(refused);
    }

    /**
     * Leaves the commands outside the list out of tab completion.
     *
     * @param event the commands sent to a player
     */
    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        if (event.getPlayer().hasPermission(ALL)) return;
        event.getCommands().removeIf(command -> !allows(command));
    }
}
