/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.company.CompanyKickEvent;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.messages.Replacement;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class Kick extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;

    public Kick(Plugin plugin, ACompanyData companyData) {
        super(plugin, CommandMeta.builder("kick")
                .addArgument("name", true)
                .build());
        this.companyData = companyData;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        companyData.retrievePlayerCompanyProfile(player)
                   .asFuture()
                   .thenAccept(optProfile -> handleProfile(player, arguments.asString(0), player, optProfile));
    }

    private void handleProfile(@NotNull Player sender, @NotNull String arg, Player player, Optional<CompanyProfile> optProfile) {
        if (optProfile.isEmpty()) {
            messageSender().sendErrorActionBar(sender, "error.noMember");
            return;
        }
        var profile = optProfile.get();

        if (!profile.member(player)
                    .map(r -> r.hasPermissions(CompanyPermission.KICK))
                    .orElse(false)) {
            messageSender().sendErrorActionBar(sender, "error.permission.kick");
            return;
        }

        var optMember = profile.memberByName(arg);

        if (optMember.isEmpty()) {
            messageSender().sendErrorActionBar(sender, "error.noCompanyMember");
            return;
        }

        var target = optMember.get();

        if (target.hasPermission(CompanyPermission.KICK)) {
            messageSender().sendErrorActionBar(sender, "error.cantKick");
            return;
        }

        companyData.submitMemberUpdate(target.kick())
                   .join();
        messageSender().sendMessage(sender, "company.kick.kicked",
                Replacement.create("name", target.player()
                                                 .getName(), Style.style()
                                                                  .color(NamedTextColor.GOLD)
                                                                  .build()));

        plugin().getServer()
                .getPluginManager()
                .callEvent(new CompanyKickEvent(optProfile.get(), target.player()));
    }
}
