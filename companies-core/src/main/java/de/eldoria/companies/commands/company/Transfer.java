/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.services.notifications.NotificationService;
import de.eldoria.companies.util.Confirmations;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.Replacement;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Hands the company to another member after the owner confirmed it. The new owner is notified.
 */
public class Transfer extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final NotificationService notificationService;
    private final Confirmations<Pending> transfers = new Confirmations<>();

    public Transfer(Plugin plugin, ACompanyData companyData, NotificationService notificationService) {
        super(
                plugin,
                CommandMeta.builder("transfer").addArgument("words.name", true).build());
        this.companyData = companyData;
        this.notificationService = notificationService;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments)
            throws CommandException {
        if ("confirm".equalsIgnoreCase(arguments.asString(0))) {
            confirm(player);
            return;
        }
        var name = arguments.asString(0);
        companyData.retrievePlayerCompanyProfile(player).whenComplete(optProfile -> check(
                        player, optProfile, profile -> profile.memberByName(name))
                .ifPresent(target -> request(player, optProfile.get(), target)));
    }

    private void request(Player player, CompanyProfile company, CompanyMember target) {
        transfers.request(player.getUniqueId(), new Pending(company.id(), target.uuid()));
        var composer = MessageComposer.create()
                .text("<neutral>")
                .localeCode(
                        "company.transfer.confirm",
                        Replacement.unparsed(
                                "name", String.valueOf(target.player().getName())))
                .space()
                .text("<click:run_command:/company transfer confirm><remove>[")
                .localeCode("words.confirm")
                .text("]</click>");
        messageSender().sendMessage(player, composer);
    }

    private void confirm(Player player) {
        var optPending = transfers.confirm(player.getUniqueId());
        if (optPending.isEmpty()) {
            messageSender().sendErrorActionBar(player, "error.confirmationExpired");
            return;
        }
        var pending = optPending.get();
        var targetPlayer = plugin().getServer().getOfflinePlayer(pending.target());
        companyData.retrievePlayerCompanyProfile(player).whenComplete(optProfile -> {
            if (optProfile.isPresent() && optProfile.get().id() != pending.company()) {
                messageSender().sendErrorActionBar(player, "error.noMember");
                return;
            }
            check(player, optProfile, profile -> profile.member(targetPlayer))
                    .ifPresent(target -> transfer(player, optProfile.get(), target));
        });
    }

    private void transfer(Player player, CompanyProfile company, CompanyMember target) {
        companyData
                .submitOwnerTransfer(company, player.getUniqueId(), target.uuid())
                .whenComplete(transferred -> {
                    if (!Boolean.TRUE.equals(transferred)) {
                        messageSender().sendError(player, "company.transfer.failed");
                        return;
                    }
                    var name = Replacement.unparsed(
                            "name", String.valueOf(target.player().getName()));
                    messageSender().sendMessage(player, "company.transfer.done", name);
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
            messageSender().sendErrorActionBar(player, "error.noMember");
            return Optional.empty();
        }
        var profile = optProfile.get();
        var member = profile.member(player).orElseThrow();
        var target = lookup.apply(profile);
        switch (Ownership.transferCheck(member, target)) {
            case NOT_OWNER -> messageSender().sendErrorActionBar(player, "error.noOwner");
            case NOT_MEMBER -> messageSender().sendErrorActionBar(player, "error.noCompanyMember");
            case SELF -> messageSender().sendErrorActionBar(player, "company.transfer.self");
            case ALLOWED -> {
                return target;
            }
        }
        return Optional.empty();
    }

    private record Pending(int company, UUID target) {}
}
