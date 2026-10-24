/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.util.Permission.Orders;
import de.eldoria.companies.util.Threading;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;

/**
 * {@code /order create <name>}: starts an order draft and shows the builder. A running draft is shown again instead.
 */
@Subcommand("create")
@Permission(Orders.CREATE)
@PlayerOnly
public final class OrderCreateCommand {
    private final CommandDependencies deps;
    private final OrderDraftCommand draft;

    /**
     * @param deps  what the commands need
     * @param draft shows the builder
     */
    public OrderCreateCommand(CommandDependencies deps, OrderDraftCommand draft) {
        this.deps = deps;
        this.draft = draft;
    }

    /**
     * @param sender sender of the command
     * @param name   name of the order
     */
    @Executes
    public void create(CommandSender sender, @StringArg(StringArgType.GREEDY) String name) {
        var player = Senders.player(sender);
        deps.requireReady();
        var running = deps.drafts().draft(player.getUniqueId());
        if (running.isPresent()) {
            draft.show(player, running.get());
            return;
        }
        deps.drafts()
                .start(player, name)
                .whenCompleteAsync(
                        (started, err) -> {
                            if (err != null) {
                                draft.report(sender, err);
                                return;
                            }
                            draft.show(player, started);
                        },
                        Threading.mainThread(deps.plugin()));
    }
}
