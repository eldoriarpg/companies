/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.PendingInviteCompanies;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.company.Application;
import de.eldoria.companies.data.wrapper.company.Invite;
import de.eldoria.companies.menus.company.InvitesMenu;
import de.eldoria.companies.services.MembershipService;
import de.eldoria.companies.services.MembershipService.Outcome;
import de.eldoria.companies.util.Permission.Company;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * {@code /company invites}: the invites the sender received, the sender's applications and, for members who may
 * invite, the invites their company sent. {@code accept} and {@code deny} answer an invite by the company's name.
 */
@Subcommand("invites")
@PlayerOnly
public final class InvitesCommand {
    private final CommandDependencies deps;
    private final MembershipReplies replies;

    /**
     * @param deps what the commands need
     */
    public InvitesCommand(CommandDependencies deps) {
        this.deps = deps;
        this.replies = new MembershipReplies(deps);
    }

    /**
     * Shows the invites of the sender.
     *
     * @param sender sender of the command
     */
    @Executes
    public void show(CommandSender sender) {
        show(Senders.player(sender));
    }

    /**
     * Joins a company that invited the sender.
     *
     * @param sender  sender of the command
     * @param company name of the company
     */
    @Executes("accept")
    @Permission(Company.JOIN)
    public void accept(CommandSender sender, @PendingInviteCompanies @StringArg(StringArgType.GREEDY) String company) {
        var player = Senders.player(sender);
        var service = deps.services().membershipService();
        replies.replyTo(
                player,
                received(player, company),
                Outcome.failure("company.invite.error.noPending"),
                invite -> service.acceptInvite(player, invite.company()));
    }

    /**
     * Declines an invite.
     *
     * @param sender  sender of the command
     * @param company name of the inviting company
     */
    @Executes("deny")
    public void deny(CommandSender sender, @PendingInviteCompanies @StringArg(StringArgType.GREEDY) String company) {
        var player = Senders.player(sender);
        var service = deps.services().membershipService();
        replies.replyTo(
                player,
                received(player, company),
                Outcome.failure("company.invite.error.noPending"),
                invite -> service.denyInvite(player, invite.company()));
    }

    /**
     * Shows the invites of a player.
     *
     * @param player viewing player
     */
    public void show(Player player) {
        var services = deps.services();
        var menu = new InvitesMenu(deps.messages(), new Buttons());
        services.membershipService()
                .invites(player)
                .thenAccept(overview -> services.menus().show(player, menu, overview))
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    private CompletableFuture<Optional<Invite>> received(Player player, String company) {
        return deps.services().membershipService().invites(player).thenApply(overview -> overview.received().stream()
                .filter(invite -> invite.companyName().equalsIgnoreCase(company))
                .findFirst());
    }

    /**
     * The buttons of the invite menu. Accepting needs the permission to join, as the command does.
     */
    private final class Buttons implements InvitesMenu.Handlers {
        @Override
        public void accept(Player player, Invite invite) {
            if (!player.hasPermission(Company.JOIN)) {
                replies.replyToButton(player, CompletableFuture.completedFuture(Outcome.failure("error.canNotJoin")));
                return;
            }
            replies.replyToButton(player, service().acceptInvite(player, invite.company()));
        }

        @Override
        public void deny(Player player, Invite invite) {
            replies.replyToButton(player, service().denyInvite(player, invite.company()));
        }

        @Override
        public void revoke(Player player, Invite invite) {
            replies.replyToButton(player, service().revokeInvite(player, invite.target()));
        }

        @Override
        public void withdraw(Player player, Application application) {
            replies.replyToButton(player, service().withdraw(player, application.company()));
        }

        private MembershipService service() {
            return deps.services().membershipService();
        }
    }
}
