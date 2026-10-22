/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.company.CompanyLeaveEvent;
import de.eldoria.companies.util.Confirmations;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /company leave}: removes a member from their company after they confirmed it.
 * <p>
 * The owner can not leave: with other members left they have to transfer the company first, alone they disband it.
 */
@Subcommand("leave")
@PlayerOnly
public final class Leave {
    private final CommandDependencies deps;
    private final Confirmations<Integer> leaves = new Confirmations<>();

    /**
     * @param deps what the commands need
     */
    public Leave(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Asks the sender to confirm leaving the company.
     *
     * @param sender sender of the command
     */
    @Executes
    public void leave(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        deps.companyData().retrievePlayerCompanyProfile(player).whenComplete(optProfile -> {
            if (optProfile.isEmpty()) {
                messages().sendErrorActionBar(player, "error.noMember");
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
            messages().sendMessage(player, composer);
        });
    }

    /**
     * Leaves the company the sender confirmed to leave.
     *
     * @param sender sender of the command
     */
    @Executes("confirm")
    public void confirm(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        leaves.confirm(player.getUniqueId()).ifPresentOrElse(companyId -> leave(player, companyId), () -> messages()
                .sendErrorActionBar(player, "error.confirmationExpired"));
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
        messages().sendMessage(player, composer);
        return true;
    }

    /**
     * Removes a player from the company they confirmed to leave.
     *
     * @param player    player who leaves
     * @param companyId company the player confirmed to leave
     */
    private void leave(Player player, int companyId) {
        deps.companyData().retrievePlayerCompanyProfile(player).asFuture().thenAccept(optProfile -> {
            if (optProfile.isEmpty() || optProfile.get().id() != companyId) {
                messages().sendErrorActionBar(player, "error.noMember");
                return;
            }
            var profile = optProfile.get();
            if (refuseOwner(player, profile)) return;
            deps.companyData()
                    .submitMemberUpdate(profile.member(player).get().kick())
                    .join();
            Threading.mainThread(deps.plugin()).execute(() -> deps.plugin()
                    .getServer()
                    .getPluginManager()
                    .callEvent(new CompanyLeaveEvent(profile, player)));
            messages().sendMessage(player, "company.leave.left");
        });
    }

    private MessageSender messages() {
        return deps.messages();
    }
}
