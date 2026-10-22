/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.company.CompanyLeaveEvent;
import de.eldoria.companies.util.Confirmations;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.commands.Completion;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Removes a member from their company after they confirmed it.
 * <p>
 * The owner can not leave: with other members left they have to transfer the company first, alone they disband it.
 */
public class Leave extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final Confirmations<Integer> leaves = new Confirmations<>();

    public Leave(Plugin plugin, ACompanyData companyData) {
        super(plugin, CommandMeta.builder("leave").build());
        this.companyData = companyData;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments)
            throws CommandException {
        if (!arguments.isEmpty() && "confirm".equalsIgnoreCase(arguments.asString(0))) {
            leaves.confirm(player.getUniqueId())
                    .ifPresentOrElse(companyId -> leave(player, companyId), () -> messageSender()
                            .sendErrorActionBar(player, "error.confirmationExpired"));
            return;
        }

        companyData.retrievePlayerCompanyProfile(player).whenComplete(optProfile -> {
            if (optProfile.isEmpty()) {
                messageSender().sendErrorActionBar(player, "error.noMember");
                return;
            }
            var profile = optProfile.get();
            if (refuseOwner(player, profile)) return;
            leaves.request(player.getUniqueId(), profile.id());
            var composer = MessageComposer.create()
                    .text("<neutral>")
                    .localeCode("company.leave.confirm")
                    .space()
                    .text("<click:run_command:/company leave confirm><remove>[")
                    .localeCode("words.confirm")
                    .text("]</click>");
            messageSender().sendMessage(player, composer);
        });
    }

    /**
     * Tells an owner how to give up the company instead of leaving it.
     *
     * @param player  player who wants to leave
     * @param company company of the player
     * @return true when the player owns the company and may not leave
     */
    private boolean refuseOwner(Player player, CompanyProfile company) {
        var member = company.member(player).orElseThrow();
        var composer = MessageComposer.create().text("<neutral>");
        switch (Ownership.leaveCheck(member, company.members().size())) {
            case ALLOWED -> {
                return false;
            }
            case TRANSFER_FIRST ->
                composer.localeCode("company.leave.transferFirst")
                        .space()
                        .text("<click:suggest_command:/company transfer ><modify>[")
                        .localeCode("words.transfer")
                        .text("]</click>")
                        .space()
                        .text("<click:run_command:/company disband><remove>[")
                        .localeCode("words.disband")
                        .text("]</click>");
            case DISBAND_INSTEAD ->
                composer.localeCode("company.leave.ownerAlone")
                        .space()
                        .text("<click:run_command:/company disband><remove>[")
                        .localeCode("words.disband")
                        .text("]</click>");
        }
        messageSender().sendMessage(player, composer);
        return true;
    }

    /**
     * Removes a player from the company they confirmed to leave.
     *
     * @param player    player who leaves
     * @param companyId company the player confirmed to leave
     */
    public void leave(Player player, int companyId) {
        companyData.retrievePlayerCompanyProfile(player).asFuture().thenAccept(optProfile -> {
            if (optProfile.isEmpty() || optProfile.get().id() != companyId) {
                messageSender().sendErrorActionBar(player, "error.noMember");
                return;
            }
            var profile = optProfile.get();
            if (refuseOwner(player, profile)) return;
            companyData.submitMemberUpdate(profile.member(player).get().kick()).join();
            Threading.mainThread(plugin())
                    .execute(() ->
                            plugin().getServer().getPluginManager().callEvent(new CompanyLeaveEvent(profile, player)));
            messageSender().sendMessage(player, "company.leave.left");
        });
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        if (leaves.isPending(player.getUniqueId())) {
            return Completion.complete(arguments.asString(0), "confirm");
        }
        return Collections.emptyList();
    }
}
