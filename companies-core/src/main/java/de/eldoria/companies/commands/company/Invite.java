/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.Checks;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.events.company.CompanyJoinEvent;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.util.Texts;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.messages.Replacement;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * {@code /company invite <player>}: invites an online player into the company. The invited player answers with
 * {@code accept} or {@code deny} before the invite expires.
 */
@Subcommand("invite")
@PlayerOnly
public final class Invite {
    private static final long TICKS_PER_SECOND = 20;
    private final CommandDependencies deps;
    private final Map<UUID, InviteData> invites = new ConcurrentHashMap<>();

    /**
     * @param deps what the commands need
     */
    public Invite(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Invites a player into the sender's company.
     *
     * @param sender sender of the command
     * @param target invited player
     */
    @Executes
    public void invite(CommandSender sender, Player target) {
        var player = Senders.player(sender);
        deps.requireReady();
        Checks.isTrue(target.hasPermission(Permission.Company.JOIN), "error.canNotJoin");
        var companyData = deps.companyData();

        companyData
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(company -> {
                    if (company.isEmpty()) {
                        messages().sendErrorActionBar(player, "error.noMember");
                        return;
                    }

                    if (!company.get().member(player).get().hasPermissions(CompanyPermission.INVITE)) {
                        messages().sendErrorActionBar(player, "error.permission.invite");
                        return;
                    }
                    var profile = company.get();
                    if (profile.members().size() >= maxMembers(profile)) {
                        messages().sendErrorActionBar(player, "error.companyFull");
                        return;
                    }

                    var targetCompany = companyData
                            .retrievePlayerCompany(target)
                            .asFuture()
                            .exceptionally(err -> {
                                deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                                return Optional.empty();
                            })
                            .join();
                    if (targetCompany.isPresent()) {
                        messages().sendErrorActionBar(player, "error.hasCompany");
                        return;
                    }
                    scheduleInvite(player, target, company.get());
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    /**
     * Joins the company the sender was invited into.
     *
     * @param sender sender of the command
     */
    @Executes("accept")
    public void accept(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        var data = invites.remove(player.getUniqueId());
        if (data == null) {
            messages().sendErrorActionBar(player, "company.invite.error.noPending");
            return;
        }
        var companyData = deps.companyData();

        companyData.retrievePlayerCompany(player).whenComplete(company -> {
            if (company.isPresent()) {
                messages().sendErrorActionBar(player, "error.alreadyMember");
                return;
            }
            var inviter = Bukkit.getOfflinePlayer(data.inviter);
            companyData.retrieveCompanyById(data.company.id()).whenComplete(current -> {
                if (current.isEmpty()) {
                    messages().sendErrorActionBar(player, "error.unknownCompany");
                    return;
                }
                companyData
                        .retrieveCompanyProfile(current.get())
                        .whenComplete(profile -> handleInviteAccept(player, data, inviter, profile));
            });
        });
    }

    /**
     * Declines the invite of the sender.
     *
     * @param sender sender of the command
     */
    @Executes("deny")
    public void deny(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        var data = invites.remove(player.getUniqueId());
        if (data == null) {
            messages().sendErrorActionBar(player, "company.invite.error.noPending");
            return;
        }
        var inviter = deps.plugin().getServer().getPlayer(data.inviter);
        if (inviter != null) {
            messages().sendErrorActionBar(inviter, "company.invite.inviteDeclined");
        }
    }

    private int maxMembers(CompanyProfile profile) {
        return deps.configuration()
                .companySettings()
                .level(profile.level())
                .orElse(CompanyLevel.DEFAULT)
                .settings()
                .maxMembers();
    }

    private void scheduleInvite(Player inviter, Player target, SimpleCompany company) {
        messages().sendMessage(inviter, "company.invite.inviteSend", Replacement.create("name", target));
        var composer = MessageComposer.create()
                .text("<neutral>")
                .localeCode(
                        "company.invite.invited",
                        Replacement.create("name", String.format("<heading>%s<neutral>", Texts.escape(company.name()))))
                .newLine()
                .text("<click:run_command:/company invite accept><add>[")
                .localeCode("words.accept")
                .text("]</click>")
                .space()
                .text("<click:run_command:/company invite deny><remove>[")
                .localeCode("words.deny")
                .text("]</click>");
        messages().sendMessage(target, composer);
        var invite = new InviteData(company, inviter.getUniqueId(), target.getName());
        invites.put(target.getUniqueId(), invite);
        var lifetime =
                TimeUnit.HOURS.toSeconds(deps.configuration().companySettings().inviteHours()) * TICKS_PER_SECOND;
        deps.plugin()
                .getServer()
                .getScheduler()
                .runTaskLater(deps.plugin(), () -> expiredInvite(target.getUniqueId(), invite), lifetime);
    }

    private void handleInviteAccept(
            Player player, InviteData data, OfflinePlayer inviter, Optional<CompanyProfile> profile) {
        if (profile.isEmpty()) {
            messages().sendErrorActionBar(player, "error.unknownCompany");
            return;
        }

        if (profile.get().members().size() >= maxMembers(profile.get())) {
            messages().sendErrorActionBar(player, "error.companyFull");
            return;
        }

        deps.companyData().submitMemberUpdate(CompanyMember.forCompany(data.company, player));
        messages().sendMessage(player, "company.invite.joined");
        if (inviter.isOnline()) {
            messages().sendMessage(inviter.getPlayer(), "company.invite.accepted", Replacement.create("name", player));
        }

        deps.plugin().getServer().getPluginManager().callEvent(new CompanyJoinEvent(profile.get(), player));
    }

    private void expiredInvite(UUID uuid, InviteData data) {
        if (invites.get(uuid) != data || !invites.remove(uuid, data)) return;
        var target = deps.plugin().getServer().getPlayer(uuid);
        if (target != null) {
            messages()
                    .sendMessage(
                            target, "company.invite.expiredTarget", Replacement.unparsed("name", data.company.name()));
        }
        var inviter = deps.plugin().getServer().getPlayer(data.inviter);
        if (inviter != null) {
            messages()
                    .sendMessage(
                            inviter, "company.invite.expiredInviter", Replacement.unparsed("name", data.targetName));
        }
    }

    private MessageSender messages() {
        return deps.messages();
    }

    private record InviteData(SimpleCompany company, UUID inviter, String targetName) {}
}
