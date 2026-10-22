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
import de.eldoria.companies.menus.ConfirmFlow;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.event.ClickEvent;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * {@code /company leave}: removes a member from their company after they confirmed it.
 * <p>
 * The owner can not leave: with other members left they have to transfer the company first, alone they disband it.
 */
@Subcommand("leave")
@PlayerOnly
public final class Leave {
    private final CommandDependencies deps;
    private final ConfirmFlow<Integer> leaves;
    private final Consumer<Player> disband;

    /**
     * @param deps    what the commands need
     * @param disband asks an owner to confirm dissolving their company
     */
    public Leave(CommandDependencies deps, Consumer<Player> disband) {
        this.deps = deps;
        this.disband = disband;
        this.leaves = ConfirmFlow.create(deps.plugin(), this::leave);
    }

    /**
     * Asks the sender to confirm leaving the company.
     *
     * @param sender sender of the command
     */
    @Executes
    public void leave(CommandSender sender) {
        var player = Senders.player(sender);
        var menus = deps.services().menus();
        deps.companyData().retrievePlayerCompanyProfile(player).whenComplete(optProfile -> {
            if (optProfile.isEmpty()) {
                messages().sendErrorActionBar(player, "error.noMember");
                return;
            }
            var profile = optProfile.get();
            if (refuseOwner(player, profile)) return;
            leaves.ask(menus, player, profile.id(), MenuText.create().locale("company.leave.confirm"));
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
        leaves.confirm(player);
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
        var text = MenuText.create().text("<neutral>");
        switch (Ownership.leaveCheck(member, company.members().size())) {
            case ALLOWED -> {
                return false;
            }
            case TRANSFER_FIRST ->
                text.locale("company.leave.transferFirst")
                        .space()
                        .text(
                                "<act_transfer><modify>[<i18n:words.transfer>]</act_transfer>",
                                Values.click("act_transfer", ClickEvent.suggestCommand("/company transfer ")))
                        .space()
                        .append(disbandButton(player));
            case DISBAND_INSTEAD ->
                text.locale("company.leave.ownerAlone").space().append(disbandButton(player));
        }
        messages().sendMessage(player, text);
        return true;
    }

    private MenuText disbandButton(Player player) {
        var actions = new MenuActions(deps.plugin(), player, MenuActions.DEFAULT_LIFETIME);
        return MenuText.create()
                .text(
                        "<act_disband><remove>[<i18n:words.disband>]</act_disband>",
                        actions.chatAction("act_disband", disband));
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
