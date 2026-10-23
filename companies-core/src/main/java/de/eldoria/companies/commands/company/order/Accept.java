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
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.events.order.OrderAcceptEvent;
import de.eldoria.eldoutilities.messages.Replacement;
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
 * The claim itself only succeeds while the order is still unclaimed.
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
        var orderData = deps.orderData();
        var configuration = deps.configuration();
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
                    var companyMember = profile.member(player).get();
                    if (!companyMember.hasPermission(CompanyPermission.MANAGE_ORDERS)) {
                        messages.sendErrorActionBar(player, "error.permission.acceptOrder");
                        return;
                    }

                    var count = orderData.retrieveCompanyOrderCount(profile).join();
                    if (count == null) {
                        messages.sendErrorActionBar(player, "error.couldNotClaim");
                        return;
                    }
                    var levelSettings = configuration.companySettings().levelSettings(profile.level());
                    if (count >= levelSettings.maxOrders()) {
                        messages.sendErrorActionBar(player, "error.orderLimit");
                        return;
                    }

                    var optOrder = orderData.retrieveOrderById(id).join();
                    if (optOrder == null || optOrder.isEmpty()) {
                        messages.sendErrorActionBar(player, "error.unknownOrder");
                        return;
                    }

                    var simpleOrder = optOrder.get();
                    if (simpleOrder.state() != OrderState.UNCLAIMED) {
                        messages.sendErrorActionBar(player, "error.orderNotClaimable");
                        return;
                    }

                    var size = orderData.retrieveFullOrder(simpleOrder).join();
                    if (size != null && levelSettings.exceedsOrderSize(size.amount())) {
                        messages.sendErrorActionBar(
                                player, "error.orderTooLarge", Replacement.create("max", levelSettings.maxOrderSize()));
                        return;
                    }

                    if (!profile.mayAcceptOrderOf(
                            simpleOrder.owner(), configuration.companySettings().allowMemberOrders())) {
                        messages.sendErrorActionBar(player, "error.ownMemberOrder");
                        return;
                    }

                    if (Boolean.TRUE.equals(orderData
                            .submitOrderClaim(profile, simpleOrder, levelSettings.deliveryHoursBonus())
                            .join())) {
                        CompanyOrderSources.invalidate(player.getUniqueId());
                        var refreshed = orderData.retrieveOrderById(id).join();
                        var claimedOrder = refreshed == null ? simpleOrder : refreshed.orElse(simpleOrder);
                        var claimed = orderData.retrieveFullOrder(claimedOrder).join();
                        menus.close(player).thenRun(() -> {
                            player.getServer().getPluginManager().callEvent(new OrderAcceptEvent(simpleOrder, profile));
                            views.renderOrder(player, profile, companyMember, claimed);
                        });
                        return;
                    }
                    messages.sendErrorActionBar(player, "error.couldNotClaim");
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }
}
