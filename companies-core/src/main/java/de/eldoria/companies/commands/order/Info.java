/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.suggest.OwnOrderIds;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;

/**
 * {@code /order info <id>}: one of the sender's orders with its progress, price and the owner's actions.
 */
@Subcommand("info")
@PlayerOnly
public final class Info {
    private final OwnerOrderViews views;

    /**
     * @param views the menus of the owner's orders
     */
    public Info(OwnerOrderViews views) {
        this.views = views;
    }

    /**
     * @param sender sender of the command
     * @param id     id of the order
     */
    @Executes
    public void info(CommandSender sender, @OwnOrderIds int id) {
        views.showOrder(Senders.player(sender), id);
    }
}
