/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.events.company.CompanyJoinEvent;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.util.Texts;
import de.eldoria.eldoutilities.commands.Completion;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.Replacement;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class Invite extends AdvancedCommand implements IPlayerTabExecutor {
    private static final long TICKS_PER_SECOND = 20;
    private final ACompanyData companyData;
    private final Map<UUID, InviteData> invites = new ConcurrentHashMap<>();
    private final Configuration configuration;

    public Invite(Plugin plugin, ACompanyData companyData, Configuration configuration) {
        super(plugin, CommandMeta.builder("invite")
                .addArgument("words.name", true)
                .build());
        this.companyData = companyData;
        this.configuration = configuration;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        if ("accept".equalsIgnoreCase(arguments.asString(0))) {
            accept(player);
            return;
        }

        if ("deny".equalsIgnoreCase(arguments.asString(0))) {
            deny(player);
            return;
        }

        var target = arguments.asPlayer(0);

        CommandAssertions.isTrue(target.hasPermission(Permission.Company.JOIN), "error.canNotJoin");

        companyData.retrievePlayerCompanyProfile(player)
                   .asFuture()
                   .exceptionally(err -> {
                       plugin().getLogger()
                               .log(Level.SEVERE, "Something went wrong", err);
                       return Optional.empty();
                   })
                   .thenAccept(company -> {
                       if (company.isEmpty()) {
                           messageSender().sendErrorActionBar(player, "error.noMember");
                           return;
                       }

                       if (!company.get()
                                   .member(player)
                                   .get()
                                   .hasPermissions(CompanyPermission.INVITE)) {
                           messageSender().sendErrorActionBar(player, "error.permission.invite");
                           return;
                       }
                       var profile = company.get();
                       if (profile.members()
                                  .size() >= configuration.companySettings()
                                                          .level(profile.level())
                                                          .orElse(CompanyLevel.DEFAULT)
                                                          .settings()
                                                          .maxMembers()) {
                           messageSender().sendErrorActionBar(player, "error.companyFull");
                           return;
                       }

                       var targetCompany = companyData.retrievePlayerCompany(target)
                                                      .asFuture()
                                                      .exceptionally(err -> {
                                                          plugin().getLogger()
                                                                  .log(Level.SEVERE, "Something went wrong", err);
                                                          return Optional.empty();
                                                      })
                                                      .join();
                       if (targetCompany.isPresent()) {
                           messageSender().sendErrorActionBar(player, "error.hasCompany");
                           return;
                       }
                       scheduleInvite(player, target, company.get());
                   })
                   .exceptionally(err -> {
                       plugin().getLogger()
                               .log(Level.SEVERE, "Something went wrong", err);
                       return null;
                   })
        ;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return Completion.completePlayers(arguments.asString(0));
    }

    private void accept(@NotNull Player player) {
        var data = invites.remove(player.getUniqueId());
        if (data == null) {
            messageSender().sendErrorActionBar(player, "company.invite.error.noPending");
            return;
        }

        companyData.retrievePlayerCompany(player)
                   .whenComplete(company -> {
                       if (company.isPresent()) {
                           messageSender().sendErrorActionBar(player, "error.alreadyMember");
                           return;
                       }
                       var inviter = Bukkit.getOfflinePlayer(data.inviter);
                       companyData.retrieveCompanyById(data.company.id())
                                  .whenComplete(current -> {
                                      if (current.isEmpty()) {
                                          messageSender().sendErrorActionBar(player, "error.unknownCompany");
                                          return;
                                      }
                                      companyData.retrieveCompanyProfile(current.get())
                                                 .whenComplete(profile -> handleInviteAccept(player, data, inviter, profile));
                                  });

                   });
    }

    private void deny(@NotNull Player player) {
        var data = invites.remove(player.getUniqueId());
        if (data == null) {
            messageSender().sendErrorActionBar(player, "company.invite.error.noPending");
            return;
        }
        var inviter = plugin().getServer()
                              .getPlayer(data.inviter);
        if (inviter != null) {
            messageSender().sendErrorActionBar(inviter, "company.invite.inviteDeclined");
        }
    }

    private void scheduleInvite(Player inviter, Player target, SimpleCompany company) {
        messageSender().sendMessage(inviter, "company.invite.inviteSend", Replacement.create("name", target));
        var composer = MessageComposer.create()
                                      .text("<neutral>")
                                      .localeCode("company.invite.invited",
                                              Replacement.create("name", String.format("<heading>%s<neutral>", Texts.escape(company.name()))))
                                      .newLine()
                                      .text("<click:run_command:/company invite accept><add>[")
                                      .localeCode("words.accept")
                                      .text("]</click>")
                                      .space()
                                      .text("<click:run_command:/company invite deny><remove>[")
                                      .localeCode("words.deny")
                                      .text("]</click>");
        messageSender().sendMessage(target, composer);
        var invite = new InviteData(company, inviter.getUniqueId(), target.getName());
        invites.put(target.getUniqueId(), invite);
        var lifetime = TimeUnit.HOURS.toSeconds(configuration.companySettings().inviteHours()) * TICKS_PER_SECOND;
        plugin().getServer()
                .getScheduler()
                .runTaskLater(plugin(), () -> expiredInvite(target.getUniqueId(), invite), lifetime);
    }

    private void handleInviteAccept(Player player, InviteData data, OfflinePlayer inviter, Optional<CompanyProfile> profile) {
        if (profile.isEmpty()) {
            messageSender().sendErrorActionBar(player, "error.unknownCompany");
            return;
        }

        if (profile.get()
                   .members()
                   .size() >= configuration.companySettings()
                                           .level(profile.get()
                                                         .level())
                                           .orElse(CompanyLevel.DEFAULT)
                                           .settings()
                                           .maxMembers()) {
            messageSender().sendErrorActionBar(player, "error.companyFull");
            return;
        }

        companyData.submitMemberUpdate(CompanyMember.forCompany(data.company, player));
        messageSender().sendMessage(player, "company.invite.joined");
        if (inviter.isOnline()) {
            messageSender().sendMessage(inviter.getPlayer(), "company.invite.accepted", Replacement.create("name", player));
        }

        plugin().getServer()
                .getPluginManager()
                .callEvent(new CompanyJoinEvent(profile.get(), player));
    }

    private void expiredInvite(UUID uuid, InviteData data) {
        if (invites.get(uuid) != data || !invites.remove(uuid, data)) return;
        var target = plugin().getServer()
                             .getPlayer(uuid);
        if (target != null) {
            messageSender().sendMessage(target, "company.invite.expiredTarget",
                    Replacement.unparsed("name", data.company.name()));
        }
        var inviter = plugin().getServer()
                              .getPlayer(data.inviter);
        if (inviter != null) {
            messageSender().sendMessage(inviter, "company.invite.expiredInviter",
                    Replacement.unparsed("name", data.targetName));
        }
    }

    private record InviteData(SimpleCompany company, UUID inviter, String targetName) {
    }
}
