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
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.menus.ConfirmFlow;
import de.eldoria.companies.menus.MenuService;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.services.notifications.NotificationService;
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
 * {@code /company transfer <member>}: hands the company to another member after the owner confirmed it. The new
 * owner is notified.
 */
@Subcommand("transfer")
@PlayerOnly
public final class Transfer {
    private final CommandDependencies deps;
    private final ConfirmFlow<Pending> transfers;

    /**
     * @param deps what the commands need
     */
    public Transfer(CommandDependencies deps) {
        this.deps = deps;
        this.transfers = ConfirmFlow.create(deps.plugin(), this::transferConfirmed);
    }

    /**
     * Asks the owner to confirm handing the company to a member.
     *
     * @param sender sender of the command
     * @param member name of the member
     */
    @Executes
    public void transfer(CommandSender sender, @CompanyMembers String member) {
        var player = Senders.player(sender);
        var menus = deps.services().menus();
        deps.companyData().retrievePlayerCompanyProfile(player).whenComplete(optProfile -> check(
                        player, optProfile, profile -> profile.memberByName(member))
                .ifPresent(target -> request(menus, player, optProfile.get(), target)));
    }

    /**
     * Hands the company to the member the owner confirmed.
     *
     * @param sender sender of the command
     */
    @Executes("confirm")
    public void confirm(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        transfers.confirm(player);
    }

    private void transferConfirmed(Player player, Pending pending) {
        var notificationService = deps.services().notificationService();
        var targetPlayer = deps.plugin().getServer().getOfflinePlayer(pending.target());
        deps.companyData().retrievePlayerCompanyProfile(player).whenComplete(optProfile -> {
            if (optProfile.isPresent() && optProfile.get().id() != pending.company()) {
                messages().sendErrorActionBar(player, "error.noMember");
                return;
            }
            check(player, optProfile, profile -> profile.member(targetPlayer))
                    .ifPresent(target -> transfer(player, optProfile.get(), target, notificationService));
        });
    }

    private void request(MenuService menus, Player player, CompanyProfile company, CompanyMember target) {
        var question = MenuText.create()
                .locale(
                        "company.transfer.confirm",
                        Values.text(
                                "member_name", String.valueOf(target.player().getName())));
        transfers.ask(menus, player, new Pending(company.id(), target.uuid()), question);
    }

    private void transfer(
            Player player, CompanyProfile company, CompanyMember target, NotificationService notificationService) {
        deps.companyData()
                .submitOwnerTransfer(company, player.getUniqueId(), target.uuid())
                .whenComplete(transferred -> {
                    if (!Boolean.TRUE.equals(transferred)) {
                        messages().sendError(player, "company.transfer.failed");
                        return;
                    }
                    var name = Replacement.unparsed(
                            "name", String.valueOf(target.player().getName()));
                    messages().sendMessage(player, "company.transfer.done", name);
                    notificationService.sendMessage(
                            target.player(),
                            "notification.ownerTransferred",
                            Replacement.unparsed("company", company.name()));
                });
    }

    private Optional<CompanyMember> check(
            Player player,
            Optional<CompanyProfile> optProfile,
            Function<CompanyProfile, Optional<CompanyMember>> lookup) {
        if (optProfile.isEmpty()) {
            messages().sendErrorActionBar(player, "error.noMember");
            return Optional.empty();
        }
        var profile = optProfile.get();
        var member = profile.member(player).orElseThrow();
        var target = lookup.apply(profile);
        switch (Ownership.transferCheck(member, target)) {
            case NOT_OWNER -> messages().sendErrorActionBar(player, "error.noOwner");
            case NOT_MEMBER -> messages().sendErrorActionBar(player, "error.noCompanyMember");
            case SELF -> messages().sendErrorActionBar(player, "company.transfer.self");
            case ALLOWED -> {
                return target;
            }
        }
        return Optional.empty();
    }

    private MessageSender messages() {
        return deps.messages();
    }

    private record Pending(int company, UUID target) {}
}
