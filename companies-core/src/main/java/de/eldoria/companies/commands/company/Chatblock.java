/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;

/**
 * {@code /company chatblock <state>}: holds or releases the chat of the sender while a menu is open. Reached from the
 * {@code [x]} of chat menus.
 */
@Subcommand("chatblock")
@PlayerOnly
public final class Chatblock {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public Chatblock(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * @param sender sender of the command
     * @param state  true to hold the chat, false to release it
     */
    @Executes
    public void chatblock(CommandSender sender, boolean state) {
        var player = Senders.player(sender);
        var messageBlocker = deps.services().messageBlocker();
        if (state) {
            messageBlocker.blockPlayer(player);
        } else {
            messageBlocker.unblockPlayer(player);
        }
    }
}
