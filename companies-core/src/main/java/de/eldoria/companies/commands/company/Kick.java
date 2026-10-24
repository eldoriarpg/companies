/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.CompanyMembers;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.menus.ConfirmFlow;
import de.eldoria.companies.menus.MenuService;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.services.ActionResult;
import de.eldoria.companies.services.MemberService;
import de.eldoria.companies.services.MemberService.MemberRef;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.messages.Replacement;
import de.eldoria.eldoutilities.threading.futures.BukkitFutureResult;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * {@code /company kick <member>}: removes a member from the company after the kicker confirmed it.
 * <p>
 * The owner kicks anyone, a member with {@link CompanyPermission#KICK} kicks members without it, and nobody kicks
 * the owner. The checks and the kick are made by {@link MemberService}.
 */
@Subcommand("kick")
@PlayerOnly
public final class Kick {
    private final CommandDependencies deps;
    private final ConfirmFlow<UUID> kicks;

    /**
     * @param deps what the commands need
     */
    public Kick(CommandDependencies deps) {
        this.deps = deps;
        this.kicks = ConfirmFlow.create(deps.plugin(), this::kickConfirmed);
    }

    /**
     * Asks the sender to confirm kicking a member.
     *
     * @param sender sender of the command
     * @param member name of the member
     */
    @Executes
    public void kick(CommandSender sender, @CompanyMembers String member) {
        var player = Senders.player(sender);
        var menus = deps.services().menus();
        var members = deps.services().members();
        BukkitFutureResult.of(members.kickable(player.getUniqueId(), MemberRef.byName(member)))
                .whenComplete(result -> {
                    switch (result) {
                        case ActionResult.Refused<CompanyMember> refused ->
                            refused.failure().sendTo(player, messages());
                        case ActionResult.Done<CompanyMember> done -> requestConfirmation(menus, player, done.value());
                    }
                });
    }

    /**
     * Kicks the member the sender confirmed to kick.
     *
     * @param sender sender of the command
     */
    @Executes("confirm")
    public void confirm(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        kicks.confirm(player);
    }

    private void kickConfirmed(Player player, UUID targetId) {
        var members = deps.services().members();
        BukkitFutureResult.of(members.kick(player.getUniqueId(), targetId)).whenComplete(result -> {
            switch (result) {
                case ActionResult.Refused<MemberService.Affected> refused ->
                    refused.failure().sendTo(player, messages());
                case ActionResult.Done<MemberService.Affected> done ->
                    messages()
                            .sendMessage(
                                    player,
                                    "company.kick.kicked",
                                    Replacement.unparsed(
                                            "name",
                                            done.value().member().player().getName()));
            }
        });
    }

    private void requestConfirmation(MenuService menus, Player player, CompanyMember target) {
        var question = MenuText.create()
                .locale(
                        "company.kick.confirm",
                        Values.text(
                                "member_name", String.valueOf(target.player().getName())));
        kicks.ask(menus, player, target.uuid(), question);
    }

    private MessageSender messages() {
        return deps.messages();
    }
}
