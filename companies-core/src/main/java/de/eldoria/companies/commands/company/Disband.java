/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.menus.ConfirmFlow;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * {@code /company disband}: dissolves the company of its owner after the owner confirmed it.
 */
@Subcommand("disband")
@PlayerOnly
public final class Disband {
    private final CommandDependencies deps;
    private final ConfirmFlow<Integer> disbands;

    /**
     * @param deps what the commands need
     */
    public Disband(CommandDependencies deps) {
        this.deps = deps;
        this.disbands = ConfirmFlow.create(deps.plugin(), this::disband);
    }

    /**
     * Asks the owner to confirm dissolving the company.
     *
     * @param sender sender of the command
     */
    @Executes
    public void disband(CommandSender sender) {
        var player = Senders.player(sender);
        var menus = deps.services().menus();
        deps.companyData().retrievePlayerCompanyProfile(player).whenComplete(optProfile -> {
            if (optProfile.isEmpty()) {
                messages().sendErrorActionBar(player, "error.noMember");
                return;
            }
            var profile = optProfile.get();
            if (!profile.member(player).orElseThrow().isOwner()) {
                messages().sendErrorActionBar(player, "error.noOwner");
                return;
            }
            disbands.ask(menus, player, profile.id(), MenuText.create().locale("company.disband.confirm"));
        });
    }

    /**
     * Dissolves the company the owner confirmed to dissolve.
     *
     * @param sender sender of the command
     */
    @Executes("confirm")
    public void confirm(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        disbands.confirm(player);
    }

    private void disband(Player player, int companyId) {
        var disbandService = deps.services().disbandService();
        deps.companyData()
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .thenAccept(optProfile -> {
                    if (optProfile.isEmpty() || optProfile.get().id() != companyId) {
                        messages().sendErrorActionBar(player, "error.noMember");
                        return;
                    }
                    var profile = optProfile.get();
                    if (!profile.member(player).orElseThrow().isOwner()) {
                        messages().sendErrorActionBar(player, "error.noOwner");
                        return;
                    }
                    if (!disbandService.disband(profile)) {
                        messages().sendError(player, "company.disband.failed");
                        return;
                    }
                    messages().sendMessage(player, "company.disband.disbanded");
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not disband a company", err);
                    return null;
                });
    }

    private MessageSender messages() {
        return deps.messages();
    }
}
