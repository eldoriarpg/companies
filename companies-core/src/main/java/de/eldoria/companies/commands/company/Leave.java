/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.menus.ConfirmFlow;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.services.ActionResult;
import de.eldoria.companies.services.MemberService;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.threading.futures.BukkitFutureResult;
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
 * The checks and the leaving are made by {@link MemberService}.
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
        var members = deps.services().members();
        BukkitFutureResult.of(members.leavable(player.getUniqueId())).whenComplete(result -> {
            switch (result) {
                case ActionResult.Refused<CompanyProfile> refused -> refuse(player, refused.failure());
                case ActionResult.Done<CompanyProfile> done ->
                    leaves.ask(
                            menus, player, done.value().id(), MenuText.create().locale("company.leave.confirm"));
            }
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
     * Tells the player why they can not leave. An owner learns how to give up the company instead.
     *
     * @param player  player who wants to leave
     * @param refusal why the player can not leave
     */
    private void refuse(Player player, CommandFailure refusal) {
        var text = MenuText.create().text("<neutral>");
        switch (refusal.localeKey()) {
            case MemberService.LEAVE_TRANSFER_FIRST ->
                text.locale(MemberService.LEAVE_TRANSFER_FIRST)
                        .space()
                        .text(
                                "<act_transfer><modify>[<i18n:words.transfer>]</act_transfer>",
                                Values.click("act_transfer", ClickEvent.suggestCommand("/company transfer ")))
                        .space()
                        .append(disbandButton(player));
            case MemberService.LEAVE_OWNER_ALONE ->
                text.locale(MemberService.LEAVE_OWNER_ALONE).space().append(disbandButton(player));
            default -> {
                refusal.sendTo(player, messages());
                return;
            }
        }
        messages().sendMessage(player, text);
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
        deps.services().members().leave(player.getUniqueId(), companyId).thenAccept(result -> {
            switch (result) {
                case ActionResult.Refused<CompanyProfile> refused -> refuse(player, refused.failure());
                case ActionResult.Done<CompanyProfile> done -> messages().sendMessage(player, "company.leave.left");
            }
        });
    }

    private MessageSender messages() {
        return deps.messages();
    }
}
