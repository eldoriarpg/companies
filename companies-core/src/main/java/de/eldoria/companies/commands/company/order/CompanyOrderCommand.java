/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.company.order.search.CompanyOrderSearchCommand;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;

/**
 * {@code /company order}: the orders of the sender's company. Without a subcommand it lists the orders the company
 * works on.
 */
@Subcommand("order")
@PlayerOnly
public final class CompanyOrderCommand {
    private final CommandDependencies deps;
    private final CompanyOrderViews views;

    @Subcommand
    public final Info info;

    @Subcommand
    public final Accept accept;

    @Subcommand
    public final Abort abort;

    @Subcommand
    public final Deliver deliver;

    @Subcommand
    public final CompanyOrderSearchCommand search;

    /**
     * @param deps what the commands need
     */
    public CompanyOrderCommand(CommandDependencies deps) {
        this.deps = deps;
        views = new CompanyOrderViews(deps);
        info = new Info(deps, views);
        accept = new Accept(deps, views);
        abort = new Abort(deps, views);
        deliver = new Deliver(deps, views);
        search = new CompanyOrderSearchCommand(deps, info::info, accept::accept);
        views.bind(new CompanyOrderViews.Commands(info, accept, abort, deliver, search));
    }

    /**
     * Lists the orders the sender's company works on.
     *
     * @param sender sender of the command
     */
    @Executes
    @Executes("list")
    public void list(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        views.showOrders(player);
    }
}
