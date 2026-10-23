/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands;

import de.eldoria.companies.commands.company.AlertsCommand;
import de.eldoria.companies.commands.company.ApplicationsCommand;
import de.eldoria.companies.commands.company.ApplyCommand;
import de.eldoria.companies.commands.company.BankCommand;
import de.eldoria.companies.commands.company.BoardCommand;
import de.eldoria.companies.commands.company.Create;
import de.eldoria.companies.commands.company.Disband;
import de.eldoria.companies.commands.company.HistoryCommand;
import de.eldoria.companies.commands.company.InboxCommand;
import de.eldoria.companies.commands.company.InviteCommand;
import de.eldoria.companies.commands.company.InvitesCommand;
import de.eldoria.companies.commands.company.JoinCommand;
import de.eldoria.companies.commands.company.JoinModeCommand;
import de.eldoria.companies.commands.company.Kick;
import de.eldoria.companies.commands.company.Leave;
import de.eldoria.companies.commands.company.Permission;
import de.eldoria.companies.commands.company.Rename;
import de.eldoria.companies.commands.company.Returns;
import de.eldoria.companies.commands.company.TopCommand;
import de.eldoria.companies.commands.company.Transfer;
import de.eldoria.companies.commands.company.member.MemberCommand;
import de.eldoria.companies.commands.company.order.CompanyOrderCommand;
import de.eldoria.companies.commands.company.order.search.OrderSearch;
import de.eldoria.companies.commands.company.profile.ProfileCommand;
import de.eldoria.companies.commands.support.HandleFailures;
import de.eldoria.companies.commands.support.PlayerOnly;
import net.strokkur.commands.Aliases;
import net.strokkur.commands.Command;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.paper.Description;
import org.bukkit.command.CommandSender;

/**
 * {@code /company}: founding, joining and running a company. Without a subcommand it shows the sender's company.
 */
@Command("company")
@Aliases("comp")
@Description("Manage your company")
@HandleFailures
final class CompanyCommand {
    @Subcommand
    final ProfileCommand profile;

    @Subcommand
    final Create create;

    @Subcommand
    final InviteCommand invite;

    @Subcommand
    final InvitesCommand invites;

    @Subcommand
    final JoinCommand join;

    @Subcommand
    final ApplyCommand apply;

    @Subcommand
    final ApplicationsCommand applications;

    @Subcommand
    final JoinModeCommand joinMode;

    @Subcommand
    final Kick kick;

    @Subcommand
    final Leave leave;

    @Subcommand
    final Transfer transfer;

    @Subcommand
    final Disband disband;

    @Subcommand
    final MemberCommand member;

    @Subcommand
    final CompanyOrderCommand order;

    @Subcommand
    final Permission permission;

    @Subcommand
    final TopCommand top;

    @Subcommand
    final Rename rename;

    @Subcommand
    final Returns returns;

    @Subcommand
    final InboxCommand inbox;

    @Subcommand
    final HistoryCommand history;

    @Subcommand
    final BoardCommand board;

    @Subcommand
    final BankCommand bank;

    @Subcommand
    final AlertsCommand alerts;

    CompanyCommand(CommandDependencies deps) {
        order = new CompanyOrderCommand(deps);
        permission = new Permission(deps);
        member = new MemberCommand(deps, permission::show);
        profile = new ProfileCommand(deps, member, order::list);
        create = new Create(deps);
        invite = new InviteCommand(deps);
        invites = new InvitesCommand(deps);
        join = new JoinCommand(deps);
        apply = new ApplyCommand(deps);
        applications = new ApplicationsCommand(deps);
        joinMode = new JoinModeCommand(deps);
        kick = new Kick(deps);
        disband = new Disband(deps);
        leave = new Leave(deps, disband::disband);
        transfer = new Transfer(deps);
        top = new TopCommand(deps, profile);
        rename = new Rename(deps);
        returns = new Returns(deps);
        inbox = new InboxCommand(deps);
        history = new HistoryCommand(deps);
        board = new BoardCommand(deps, new OrderSearch(deps, order.info::info, order.accept::accept));
        bank = new BankCommand(deps);
        alerts = new AlertsCommand(deps, new OrderSearch(deps, order.info::info, order.accept::accept));
    }

    /**
     * Shows the profile of the sender's company.
     *
     * @param sender sender of the command
     */
    @Executes
    @PlayerOnly
    void profile(CommandSender sender) {
        profile.self(sender);
    }
}
