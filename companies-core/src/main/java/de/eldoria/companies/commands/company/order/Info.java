/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.CompanyOrderIds;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.order.OrderState;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;

import java.util.Optional;
import java.util.logging.Level;

/**
 * {@code /company order info <id>}: the company view of an order the company works on or could accept.
 */
@Subcommand("info")
@PlayerOnly
public final class Info {
    private final CommandDependencies deps;
    private final CompanyOrderViews views;

    /**
     * @param deps  what the commands need
     * @param views the menus of the company orders
     */
    public Info(CommandDependencies deps, CompanyOrderViews views) {
        this.deps = deps;
        this.views = views;
    }

    /**
     * Shows an order of the company, or an order nobody accepted yet.
     *
     * @param sender sender of the command
     * @param id     id of the order
     */
    @Executes
    public void info(CommandSender sender, @CompanyOrderIds int id) {
        var player = Senders.player(sender);
        deps.requireReady();
        var messages = deps.messages();
        var orderData = deps.orderData();
        deps.companyData()
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messages.sendErrorActionBar(player, "error.noMember");
                        return;
                    }
                    var profile = optProfile.get();
                    var optOrder = orderData.retrieveOrderById(id).join();
                    if (optOrder == null || optOrder.isEmpty()) {
                        messages.sendErrorActionBar(player, "error.unknownOrder");
                        return;
                    }
                    var order = optOrder.get();
                    if (order.company() != profile.id() && order.state() != OrderState.UNCLAIMED) {
                        messages.sendErrorActionBar(player, "error.unknownOrder");
                        return;
                    }
                    var fullOrder = orderData.retrieveFullOrder(order).join();
                    if (fullOrder == null) {
                        messages.sendErrorActionBar(player, "error.unknownOrder");
                        return;
                    }
                    views.renderOrder(player, profile, profile.member(player).get(), fullOrder);
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }
}
