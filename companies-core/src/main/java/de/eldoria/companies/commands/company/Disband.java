/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.services.DisbandService;
import de.eldoria.companies.util.Confirmations;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/**
 * Dissolves the company of its owner after the owner confirmed it.
 */
public class Disband extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final DisbandService disbandService;
    private final Confirmations<Integer> disbands = new Confirmations<>();

    public Disband(Plugin plugin, ACompanyData companyData, DisbandService disbandService) {
        super(plugin, CommandMeta.builder("disband").build());
        this.companyData = companyData;
        this.disbandService = disbandService;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments)
            throws CommandException {
        if (!arguments.isEmpty() && "confirm".equalsIgnoreCase(arguments.asString(0))) {
            disbands.confirm(player.getUniqueId())
                    .ifPresentOrElse(companyId -> disband(player, companyId), () -> messageSender()
                            .sendErrorActionBar(player, "error.confirmationExpired"));
            return;
        }
        companyData.retrievePlayerCompanyProfile(player).whenComplete(optProfile -> {
            if (optProfile.isEmpty()) {
                messageSender().sendErrorActionBar(player, "error.noMember");
                return;
            }
            var profile = optProfile.get();
            if (!profile.member(player).orElseThrow().isOwner()) {
                messageSender().sendErrorActionBar(player, "error.noOwner");
                return;
            }
            disbands.request(player.getUniqueId(), profile.id());
            var composer = MessageComposer.create()
                    .text("<neutral>")
                    .localeCode("company.disband.confirm")
                    .space()
                    .text("<click:run_command:/company disband confirm><remove>[")
                    .localeCode("words.confirm")
                    .text("]</click>");
            messageSender().sendMessage(player, composer);
        });
    }

    private void disband(Player player, int companyId) {
        companyData
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .thenAccept(optProfile -> {
                    if (optProfile.isEmpty() || optProfile.get().id() != companyId) {
                        messageSender().sendErrorActionBar(player, "error.noMember");
                        return;
                    }
                    var profile = optProfile.get();
                    if (!profile.member(player).orElseThrow().isOwner()) {
                        messageSender().sendErrorActionBar(player, "error.noOwner");
                        return;
                    }
                    if (!disbandService.disband(profile)) {
                        messageSender().sendError(player, "company.disband.failed");
                        return;
                    }
                    messageSender().sendMessage(player, "company.disband.disbanded");
                })
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Could not disband a company", err);
                    return null;
                });
    }
}
