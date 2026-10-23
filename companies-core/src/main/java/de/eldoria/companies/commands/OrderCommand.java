/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands;

import de.eldoria.companies.commands.order.Cancel;
import de.eldoria.companies.commands.order.Collect;
import de.eldoria.companies.commands.order.Extend;
import de.eldoria.companies.commands.order.HistoryCommand;
import de.eldoria.companies.commands.order.Info;
import de.eldoria.companies.commands.order.List;
import de.eldoria.companies.commands.order.OrderCreateCommand;
import de.eldoria.companies.commands.order.OrderDraftCommand;
import de.eldoria.companies.commands.order.OwnerOrderViews;
import de.eldoria.companies.commands.order.Raise;
import de.eldoria.companies.commands.order.Receive;
import de.eldoria.companies.commands.order.SeriesCommand;
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

    @Subcommand
    final OrderDraftCommand draft;

    @Subcommand
    final OrderCreateCommand create;

    @Subcommand
    final HistoryCommand history;

    @Subcommand
    final SeriesCommand series;

    OrderCommand(CommandDependencies deps) {
        var views = new OwnerOrderViews(deps);
        list = new List(deps, views);
        info = new Info(views);
        cancel = new Cancel(deps, views);
        receive = new Receive(deps);
        collect = new Collect(deps);
        extend = new Extend(deps);
        raise = new Raise(deps);
        draft = new OrderDraftCommand(deps);
        create = new OrderCreateCommand(deps, draft);
        history = new HistoryCommand(deps);
        series = new SeriesCommand(deps, views);
        views.bind(new OwnerOrderViews.Commands(cancel, extend, raise, receive, create, series));
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
