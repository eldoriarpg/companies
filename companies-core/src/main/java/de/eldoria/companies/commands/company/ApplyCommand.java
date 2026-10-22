/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.ApplicableCompanyNames;
import de.eldoria.companies.commands.suggest.OwnApplicationCompanies;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.services.MembershipService.Outcome;
import de.eldoria.companies.util.Permission.Company;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;

/**
 * {@code /company apply <company> <message>}: applies to a company that accepts applications. A company name with
 * spaces is written in quotes. {@code /company apply withdraw <company>} takes the application back.
 */
@Subcommand("apply")
@Permission(Company.JOIN)
@PlayerOnly
public final class ApplyCommand {
    private final CommandDependencies deps;
    private final MembershipReplies replies;

    /**
     * @param deps what the commands need
     */
    public ApplyCommand(CommandDependencies deps) {
        this.deps = deps;
        this.replies = new MembershipReplies(deps);
    }

    /**
     * Applies to a company.
     *
     * @param sender  sender of the command
     * @param company name of the company
     * @param message message to the members
     */
    @Executes
    public void apply(
            CommandSender sender,
            @ApplicableCompanyNames @StringArg(StringArgType.STRING) String company,
            @StringArg(StringArgType.GREEDY) String message) {
        var player = Senders.player(sender);
        replies.reply(player, deps.services().membershipService().apply(player, company, message));
    }

    /**
     * Takes back an application.
     *
     * @param sender  sender of the command
     * @param company name of the company
     */
    @Executes("withdraw")
    public void withdraw(
            CommandSender sender, @OwnApplicationCompanies @StringArg(StringArgType.GREEDY) String company) {
        var player = Senders.player(sender);
        var service = deps.services().membershipService();
        var application = service.invites(player).thenApply(overview -> overview.applications().stream()
                .filter(own -> own.companyName().equalsIgnoreCase(company))
                .findFirst());
        replies.replyTo(
                player,
                application,
                Outcome.failure("company.apply.error.noPending"),
                own -> service.withdraw(player, own.company()));
    }
}
