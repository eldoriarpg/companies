/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.CompanyMembers;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.ICompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.company.CompanyKickEvent;
import de.eldoria.companies.menus.ConfirmFlow;
import de.eldoria.companies.menus.MenuService;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.messages.Replacement;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * {@code /company kick <member>}: removes a member from the company after the kicker confirmed it.
 * <p>
 * The owner kicks anyone, a member with {@link CompanyPermission#KICK} kicks members without it, and nobody kicks
 * the owner.
 */
@Subcommand("kick")
@PlayerOnly
public final class Kick {
    private final CommandDependencies deps;
    private final ConfirmFlow<UUID> kicks;

    /**
     * @param deps what the commands need
     */
    public Kick(CommandDependencies deps) {
        this.deps = deps;
        this.kicks = ConfirmFlow.create(deps.plugin(), this::kickConfirmed);
    }

    /**
     * Decides whether one member may kick another.
     *
     * @param kicker member who wants to kick
     * @param target member who would be kicked
     * @return true when the kick is allowed
     */
    public static boolean mayKick(ICompanyMember kicker, ICompanyMember target) {
        if (target.isOwner()) return false;
        if (kicker.isOwner()) return true;
        return kicker.hasPermission(CompanyPermission.KICK) && !target.hasPermission(CompanyPermission.KICK);
    }

    /**
     * Asks the sender to confirm kicking a member.
     *
     * @param sender sender of the command
     * @param member name of the member
     */
    @Executes
    public void kick(CommandSender sender, @CompanyMembers String member) {
        var player = Senders.player(sender);
        var menus = deps.services().menus();
        deps.companyData().retrievePlayerCompanyProfile(player).whenComplete(optProfile -> checkKick(
                        player, optProfile, profile -> profile.memberByName(member))
                .ifPresent(target -> requestConfirmation(menus, player, target)));
    }

    /**
     * Kicks the member the sender confirmed to kick.
     *
     * @param sender sender of the command
     */
    @Executes("confirm")
    public void confirm(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        kicks.confirm(player);
    }

    private void kickConfirmed(Player player, UUID targetId) {
        var targetPlayer = deps.plugin().getServer().getOfflinePlayer(targetId);
        deps.companyData().retrievePlayerCompanyProfile(player).whenComplete(optProfile -> checkKick(
                        player, optProfile, profile -> profile.member(targetPlayer))
                .ifPresent(target -> kick(player, optProfile.get(), target)));
    }

    private Optional<CompanyMember> checkKick(
            Player player,
            Optional<CompanyProfile> optProfile,
            Function<CompanyProfile, Optional<CompanyMember>> lookup) {
        if (optProfile.isEmpty()) {
            messages().sendErrorActionBar(player, "error.noMember");
            return Optional.empty();
        }
        var profile = optProfile.get();
        var kicker = profile.member(player).orElseThrow();
        if (!kicker.hasPermission(CompanyPermission.KICK)) {
            messages().sendErrorActionBar(player, "error.permission.kick");
            return Optional.empty();
        }
        var optTarget = lookup.apply(profile);
        if (optTarget.isEmpty()) {
            messages().sendErrorActionBar(player, "error.noCompanyMember");
            return Optional.empty();
        }
        if (!mayKick(kicker, optTarget.get())) {
            messages().sendErrorActionBar(player, "error.cantKick");
            return Optional.empty();
        }
        return optTarget;
    }

    private void requestConfirmation(MenuService menus, Player player, CompanyMember target) {
        var question = MenuText.create()
                .locale(
                        "company.kick.confirm",
                        Values.text(
                                "member_name", String.valueOf(target.player().getName())));
        kicks.ask(menus, player, target.uuid(), question);
    }

    private void kick(Player player, CompanyProfile profile, CompanyMember target) {
        deps.companyData().submitMemberUpdate(target.kick()).whenComplete(ignored -> {
            messages()
                    .sendMessage(
                            player,
                            "company.kick.kicked",
                            Replacement.unparsed("name", target.player().getName()));
            deps.plugin().getServer().getPluginManager().callEvent(new CompanyKickEvent(profile, target.player()));
        });
    }

    private MessageSender messages() {
        return deps.messages();
    }
}
