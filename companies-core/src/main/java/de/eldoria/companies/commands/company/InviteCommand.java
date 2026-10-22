/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.InvitedPlayers;
import de.eldoria.companies.commands.suggest.KnownPlayers;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.services.MembershipService.Outcome;
import de.eldoria.companies.util.Permission;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;

/**
 * {@code /company invite <player>} and {@code /company invite revoke <player>}: invites a player into the company or
 * takes an invite back. Invites are stored, so the player may be offline or on another server and answers within the
 * configured hours.
 */
@Subcommand("invite")
@PlayerOnly
public final class InviteCommand {
    private final CommandDependencies deps;
    private final MembershipReplies replies;

    /**
     * @param deps what the commands need
     */
    public InviteCommand(CommandDependencies deps) {
        this.deps = deps;
        this.replies = new MembershipReplies(deps);
    }

    /**
     * Invites a player into the sender's company. Whether the player may join companies is only known while they are
     * online on this server.
     *
     * @param sender sender of the command
     * @param player name of the invited player
     */
    @Executes
    public void invite(CommandSender sender, @KnownPlayers String player) {
        var inviter = Senders.player(sender);
        var service = deps.services().membershipService();
        var target = deps.plugin().getServer().getOfflinePlayerIfCached(player);
        if (target == null) throw CommandFailure.of("error.unknownPlayer");
        var online = target.getPlayer();
        var mayJoin = online == null || online.hasPermission(Permission.Company.JOIN);
        replies.reply(inviter, service.invite(inviter, target, mayJoin));
    }

    /**
     * Takes back an invite of the sender's company.
     *
     * @param sender sender of the command
     * @param player name of the invited player
     */
    @Executes("revoke")
    public void revoke(CommandSender sender, @InvitedPlayers String player) {
        var member = Senders.player(sender);
        var service = deps.services().membershipService();
        var invite = service.invites(member).thenApply(overview -> overview.sent().stream()
                .filter(sent -> overview.name(sent.target()).equalsIgnoreCase(player))
                .findFirst());
        replies.replyTo(
                member,
                invite,
                Outcome.failure("company.invite.error.notInvited", Values.text("player_name", player)),
                found -> service.revokeInvite(member, found.target()));
    }
}
