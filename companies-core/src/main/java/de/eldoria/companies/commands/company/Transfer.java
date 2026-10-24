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
import de.eldoria.companies.menus.ConfirmFlow;
import de.eldoria.companies.menus.MenuService;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.services.ActionResult;
import de.eldoria.companies.services.MemberService;
import de.eldoria.companies.services.MemberService.Affected;
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
 * {@code /company transfer <member>}: hands the company to another member after the owner confirmed it. The new
 * owner is notified.
 * <p>
 * The checks and the transfer are made by {@link MemberService}.
 */
@Subcommand("transfer")
@PlayerOnly
public final class Transfer {
    private final CommandDependencies deps;
    private final ConfirmFlow<Pending> transfers;

    /**
     * @param deps what the commands need
     */
    public Transfer(CommandDependencies deps) {
        this.deps = deps;
        this.transfers = ConfirmFlow.create(deps.plugin(), this::transferConfirmed);
    }

    /**
     * Asks the owner to confirm handing the company to a member.
     *
     * @param sender sender of the command
     * @param member name of the member
     */
    @Executes
    public void transfer(CommandSender sender, @CompanyMembers String member) {
        var player = Senders.player(sender);
        var menus = deps.services().menus();
        var members = deps.services().members();
        BukkitFutureResult.of(members.transferable(player.getUniqueId(), MemberRef.byName(member)))
                .whenComplete(result -> {
                    switch (result) {
                        case ActionResult.Refused<Affected> refused ->
                            refused.failure().sendTo(player, messages());
                        case ActionResult.Done<Affected> done -> request(menus, player, done.value());
                    }
                });
    }

    /**
     * Hands the company to the member the owner confirmed.
     *
     * @param sender sender of the command
     */
    @Executes("confirm")
    public void confirm(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        transfers.confirm(player);
    }

    private void transferConfirmed(Player player, Pending pending) {
        var members = deps.services().members();
        BukkitFutureResult.of(members.transfer(player.getUniqueId(), pending.company(), pending.target()))
                .whenComplete(result -> {
                    switch (result) {
                        case ActionResult.Refused<Affected> refused ->
                            refused.failure().sendTo(player, messages());
                        case ActionResult.Done<Affected> done -> {
                            var name = Replacement.unparsed(
                                    "name",
                                    String.valueOf(
                                            done.value().member().player().getName()));
                            messages().sendMessage(player, "company.transfer.done", name);
                        }
                    }
                });
    }

    private void request(MenuService menus, Player player, Affected transfer) {
        var target = transfer.member();
        var question = MenuText.create()
                .locale(
                        "company.transfer.confirm",
                        Values.text(
                                "member_name", String.valueOf(target.player().getName())));
        transfers.ask(menus, player, new Pending(transfer.company().id(), target.uuid()), question);
    }

    private MessageSender messages() {
        return deps.messages();
    }

    private record Pending(int company, UUID target) {}
}
