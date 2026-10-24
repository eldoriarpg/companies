/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.ApplicantNames;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.company.Application;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.menus.company.ApplicationsMenu;
import de.eldoria.companies.services.MembershipService;
import de.eldoria.companies.services.MembershipService.Outcome;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * {@code /company applications}: the open applications to the sender's company, for members who may invite.
 * {@code accept} and {@code deny} answer an application by the applicant's name.
 */
@Subcommand("applications")
@PlayerOnly
public final class ApplicationsCommand {
    private final CommandDependencies deps;
    private final MembershipReplies replies;

    /**
     * @param deps what the commands need
     */
    public ApplicationsCommand(CommandDependencies deps) {
        this.deps = deps;
        this.replies = new MembershipReplies(deps);
    }

    /**
     * Shows the applications to the sender's company.
     *
     * @param sender sender of the command
     */
    @Executes
    public void show(CommandSender sender) {
        show(Senders.player(sender));
    }

    /**
     * Accepts an application, which makes the applicant a member.
     *
     * @param sender sender of the command
     * @param player name of the applicant
     */
    @Executes("accept")
    public void accept(CommandSender sender, @ApplicantNames String player) {
        var member = Senders.player(sender);
        var service = service();
        replies.replyTo(
                member,
                application(member, player),
                noApplication(player),
                application -> service.acceptApplication(member, application.applicant()));
    }

    /**
     * Denies an application.
     *
     * @param sender sender of the command
     * @param player name of the applicant
     */
    @Executes("deny")
    public void deny(CommandSender sender, @ApplicantNames String player) {
        var member = Senders.player(sender);
        var service = service();
        replies.replyTo(
                member,
                application(member, player),
                noApplication(player),
                application -> service.denyApplication(member, application.applicant()));
    }

    /**
     * Shows the applications to the company of a member.
     *
     * @param member viewing member
     */
    public void show(Player member) {
        var services = deps.services();
        var menu = new ApplicationsMenu(deps.messages(), services.menus(), new Buttons());
        services.membershipService()
                .applications(member)
                .thenAccept(overview -> overview.refusal()
                        .ifPresentOrElse(refusal -> refusal.sendTo(member, deps.messages()), () -> services.menus()
                                .show(member, menu, ApplicationsMenu.Model.of(overview))))
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    private CompletableFuture<Optional<Application>> application(Player member, String name) {
        return service().applications(member).thenApply(overview -> overview.applications().stream()
                .filter(application -> overview.name(application.applicant()).equalsIgnoreCase(name))
                .findFirst());
    }

    private static Outcome noApplication(String name) {
        return Outcome.failure("company.applications.error.noPending", Values.text("player_name", name));
    }

    private MembershipService service() {
        return deps.services().membershipService();
    }

    /**
     * The buttons of the application menu.
     */
    private final class Buttons implements ApplicationsMenu.Handlers {
        @Override
        public void accept(Player player, Application application) {
            replies.replyToButton(player, service().acceptApplication(player, application.applicant()));
        }

        @Override
        public void deny(Player player, Application application) {
            replies.replyToButton(player, service().denyApplication(player, application.applicant()));
        }
    }
}
