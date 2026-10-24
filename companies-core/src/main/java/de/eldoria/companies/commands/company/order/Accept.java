/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.CompanyOrderSources;
import de.eldoria.companies.commands.suggest.SearchResultIds;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.orders.OrderAcceptService.Result;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.IntArg;
import org.bukkit.command.CommandSender;

import java.util.Optional;
import java.util.logging.Level;

/**
 * {@code /company order accept <id>}: claims an order for the sender's company.
 * <p>
 * The member needs {@link CompanyPermission#MANAGE_ORDERS}. The company must be below the order limit of its level,
 * the order must fit the order size of the level and must not belong to a member unless member orders are allowed.
 * The claim itself only succeeds while the order is still unclaimed. The checks, the events and the claim are made by
 * {@link de.eldoria.companies.orders.OrderAcceptService}.
 */
@Subcommand("accept")
@PlayerOnly
public final class Accept {
    private final CommandDependencies deps;
    private final CompanyOrderViews views;

    /**
     * @param deps  what the commands need
     * @param views the menus of the company orders
     */
    public Accept(CommandDependencies deps, CompanyOrderViews views) {
        this.deps = deps;
        this.views = views;
    }

    /**
     * Accepts an order for the sender's company.
     *
     * @param sender sender of the command
     * @param id     id of the order
     */
    @Executes
    public void accept(CommandSender sender, @SearchResultIds @IntArg(min = 0) int id) {
        var player = Senders.player(sender);
        var menus = deps.services().menus();
        var messages = deps.messages();
        var acceptance = deps.services().orderAccept();
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
                    switch (acceptance.accept(player, profile, id).join()) {
                        case Result.Refused refused -> refused.failure().sendTo(player, messages);
                        case Result.Accepted accepted -> {
                            CompanyOrderSources.invalidate(player.getUniqueId());
                            var member = profile.member(player).orElseThrow();
                            menus.close(player)
                                    .thenRun(() -> views.renderOrder(player, profile, member, accepted.order()));
                        }
                    }
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }
}
