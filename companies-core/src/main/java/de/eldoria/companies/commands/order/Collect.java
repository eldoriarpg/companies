/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;

/**
 * {@code /order collect}: hands out items of received orders which did not fit into the player's inventory.
 */
@Subcommand("collect")
@PlayerOnly
public final class Collect {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public Collect(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * @param sender sender of the command
     */
    @Executes
    public void collect(CommandSender sender) {
        var player = Senders.player(sender);
        deps.services().itemReturnService().handOut(player, ReturnKind.OWNER, false);
    }
}
