/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.OwnDeliveredOrderIds;
import de.eldoria.companies.commands.suggest.SuggestionSources;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.OrderReceiveService.Received;
import de.eldoria.companies.services.ActionResult;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * {@code /order receive <id>}: hands the owner of a delivered order the stacks as they were delivered.
 */
@Subcommand("receive")
@PlayerOnly
public final class Receive {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public Receive(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * @param sender sender of the command
     * @param id     id of the order
     */
    @Executes
    public void receive(CommandSender sender, @OwnDeliveredOrderIds int id) {
        var player = Senders.player(sender);
        deps.services()
                .receive()
                .receive(player, id)
                .thenAccept(result -> {
                    switch (result) {
                        case ActionResult.Refused<Received> refused ->
                            refused.failure().sendTo(player, deps.messages());
                        case ActionResult.Done<Received> done -> received(player, done.value());
                    }
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    private void received(Player player, Received received) {
        SuggestionSources.current().ifPresent(sources -> sources.ownOrders().invalidate(player.getUniqueId()));
        if (!player.isOnline()) return;
        deps.services()
                .menus()
                .close(player)
                .thenRun(() -> sendReceived(player, received.amount(), received.waiting()));
    }

    private void sendReceived(Player player, int total, int waiting) {
        if (waiting <= 0) {
            deps.messages().sendMessage(player, "order.receive.received");
            return;
        }
        var text = MenuText.create()
                .locale(
                        "order.receive.receivedPartly",
                        Values.number("received", total - waiting),
                        Values.number("waiting", waiting))
                .space()
                .text(
                        "<act_collect><add>[<i18n:words.collect>]</act_collect>",
                        deps.services().itemReturnService().handOutButton("act_collect", player, ReturnKind.OWNER));
        deps.messages().sendMessage(player, text);
    }
}
