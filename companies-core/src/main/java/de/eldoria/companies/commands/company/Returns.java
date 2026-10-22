/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;

/**
 * {@code /company returns}: hands out items which were returned to the player after an order they delivered to was
 * released.
 */
@Subcommand("returns")
@PlayerOnly
public final class Returns {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public Returns(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * @param sender sender of the command
     */
    @Executes
    public void returns(CommandSender sender) {
        var player = Senders.player(sender);
        deps.services().itemReturnService().handOut(player, ReturnKind.WORKER, false);
    }
}
