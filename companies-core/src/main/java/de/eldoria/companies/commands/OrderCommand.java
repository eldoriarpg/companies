/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands;

import de.eldoria.companies.commands.order.Cancel;
import de.eldoria.companies.commands.order.Collect;
import de.eldoria.companies.commands.order.Extend;
import de.eldoria.companies.commands.order.Info;
import de.eldoria.companies.commands.order.List;
import de.eldoria.companies.commands.order.Raise;
import de.eldoria.companies.commands.order.Receive;
import de.eldoria.companies.commands.support.HandleFailures;
import de.eldoria.companies.commands.support.PlayerOnly;
import net.strokkur.commands.Command;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.paper.Description;
import org.bukkit.command.CommandSender;

/**
 * {@code /order}: creating and managing the sender's own orders. Without a subcommand it lists them.
 */
@Command("order")
@Description("Manage your orders")
@HandleFailures
final class OrderCommand {
    @Subcommand
    final List list;

    @Subcommand
    final Info info;

    @Subcommand
    final Cancel cancel;

    @Subcommand
    final Receive receive;

    @Subcommand
    final Collect collect;

    @Subcommand
    final Extend extend;

    @Subcommand
    final Raise raise;

    // TODO create <name…>, starting a draft
    // TODO draft name|add|remove|amount|price|done|cancel …

    OrderCommand(CommandDependencies deps) {
        list = new List(deps);
        info = new Info(deps);
        cancel = new Cancel(deps, list);
        receive = new Receive(deps);
        collect = new Collect(deps);
        extend = new Extend(deps);
        raise = new Raise(deps);
    }

    /**
     * Lists the sender's orders.
     *
     * @param sender sender of the command
     */
    @Executes
    @PlayerOnly
    void list(CommandSender sender) {
        list.list(sender);
    }
}
