/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;

/**
 * {@code /order list}: the sender's orders which are not received yet, with the order limit and the items waiting
 * to be collected.
 */
@Subcommand("list")
@PlayerOnly
public final class List {
    private final CommandDependencies deps;
    private final OwnerOrderViews views;

    /**
     * @param deps  what the commands need
     * @param views the menus of the owner's orders
     */
    public List(CommandDependencies deps, OwnerOrderViews views) {
        this.deps = deps;
        this.views = views;
    }

    /**
     * @param sender sender of the command
     */
    @Executes
    public void list(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        views.showOrders(player, () -> {});
    }
}
