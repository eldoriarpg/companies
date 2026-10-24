/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.OwnOpenOrderIds;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.orders.OwnerOrderService;
import de.eldoria.companies.orders.OwnerOrderService.Extended;
import de.eldoria.companies.services.ActionResult;
import de.eldoria.companies.util.Texts;
import de.eldoria.eldoutilities.messages.Replacement;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;

import java.util.logging.Level;

/**
 * {@code /order extend <id>}: restarts the purge timer of an order nobody accepted yet, up to {@code maxExtensions}
 * times per order. The checks and the extension are made by {@link OwnerOrderService}.
 */
@Subcommand("extend")
@PlayerOnly
public final class Extend {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public Extend(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * @param sender sender of the command
     * @param id     id of the order
     */
    @Executes
    public void extend(CommandSender sender, @OwnOpenOrderIds int id) {
        var player = Senders.player(sender);
        var messages = deps.messages();
        deps.services()
                .ownerOrders()
                .extend(player.getUniqueId(), id)
                .thenAccept(result -> {
                    switch (result) {
                        case ActionResult.Refused<Extended> refused ->
                            refused.failure().sendTo(player, messages);
                        case ActionResult.Done<Extended>(var extended) ->
                            messages.sendMessage(
                                    player,
                                    "order.extend.extended",
                                    Replacement.unparsed(
                                            "order_name", extended.order().fullName()),
                                    Replacement.create("duration", Texts.prettyDuration(extended.refundIn())),
                                    Replacement.create("left", extended.left()));
                    }
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }
}
