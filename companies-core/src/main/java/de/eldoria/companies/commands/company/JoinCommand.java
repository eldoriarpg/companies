/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.JoinableCompanyNames;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.util.Permission.Company;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;

/**
 * {@code /company join <company>}: joins a company that is open to everyone.
 */
@Subcommand("join")
@Permission(Company.JOIN)
@PlayerOnly
public final class JoinCommand {
    private final CommandDependencies deps;
    private final MembershipReplies replies;

    /**
     * @param deps what the commands need
     */
    public JoinCommand(CommandDependencies deps) {
        this.deps = deps;
        this.replies = new MembershipReplies(deps);
    }

    /**
     * Joins an open company.
     *
     * @param sender  sender of the command
     * @param company name of the company
     */
    @Executes
    public void join(CommandSender sender, @JoinableCompanyNames @StringArg(StringArgType.GREEDY) String company) {
        var player = Senders.player(sender);
        replies.reply(player, deps.services().membershipService().join(player, company));
    }
}
