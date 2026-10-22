/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.ICompanyMember;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.company.CompanyKickEvent;
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
 * Removes a member from the company after the kicker confirmed it.
 * <p>
 * The owner kicks anyone, a member with {@link CompanyPermission#KICK} kicks members without it, and nobody kicks
 * the owner.
 */
public class Kick extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final Confirmations<UUID> kicks = new Confirmations<>();

    public Kick(Plugin plugin, ACompanyData companyData) {
        super(plugin, CommandMeta.builder("kick")
                .addArgument("name", true)
                .build());
        this.companyData = companyData;
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

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        if ("confirm".equalsIgnoreCase(arguments.asString(0))) {
            confirm(player);
            return;
        }
        var name = arguments.asString(0);
        companyData.retrievePlayerCompanyProfile(player)
                   .whenComplete(optProfile -> checkKick(player, optProfile, profile -> profile.memberByName(name))
                           .ifPresent(target -> requestConfirmation(player, target)));
    }

    private void confirm(Player player) {
        var optTarget = kicks.confirm(player.getUniqueId());
        if (optTarget.isEmpty()) {
            messageSender().sendErrorActionBar(player, "error.confirmationExpired");
            return;
        }
        var targetPlayer = plugin().getServer().getOfflinePlayer(optTarget.get());
        companyData.retrievePlayerCompanyProfile(player)
                   .whenComplete(optProfile -> checkKick(player, optProfile, profile -> profile.member(targetPlayer))
                           .ifPresent(target -> kick(player, optProfile.get(), target)));
    }

    private Optional<CompanyMember> checkKick(Player player, Optional<CompanyProfile> optProfile, Function<CompanyProfile, Optional<CompanyMember>> lookup) {
        if (optProfile.isEmpty()) {
            messageSender().sendErrorActionBar(player, "error.noMember");
            return Optional.empty();
        }
        var profile = optProfile.get();
        var kicker = profile.member(player).orElseThrow();
        if (!kicker.hasPermission(CompanyPermission.KICK)) {
            messageSender().sendErrorActionBar(player, "error.permission.kick");
            return Optional.empty();
        }
        var optTarget = lookup.apply(profile);
        if (optTarget.isEmpty()) {
            messageSender().sendErrorActionBar(player, "error.noCompanyMember");
            return Optional.empty();
        }
        if (!mayKick(kicker, optTarget.get())) {
            messageSender().sendErrorActionBar(player, "error.cantKick");
            return Optional.empty();
        }
        return optTarget;
    }

    private void requestConfirmation(Player player, CompanyMember target) {
        kicks.request(player.getUniqueId(), target.uuid());
        var composer = MessageComposer.create()
                                      .text("<neutral>")
                                      .localeCode("company.kick.confirm", Replacement.unparsed("name", target.player().getName()))
                                      .space()
                                      .text("<click:run_command:/company kick confirm><remove>[")
                                      .localeCode("words.confirm")
                                      .text("]</click>");
        messageSender().sendMessage(player, composer);
    }

    private void kick(Player player, CompanyProfile profile, CompanyMember target) {
        companyData.submitMemberUpdate(target.kick())
                   .whenComplete(ignored -> {
                       messageSender().sendMessage(player, "company.kick.kicked",
                               Replacement.unparsed("name", target.player().getName()));
                       plugin().getServer()
                               .getPluginManager()
                               .callEvent(new CompanyKickEvent(profile, target.player()));
                   });
    }
}
