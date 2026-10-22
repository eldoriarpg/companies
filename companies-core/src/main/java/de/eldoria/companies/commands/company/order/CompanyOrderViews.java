/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.eldoutilities.localization.MessageComposer;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.logging.Level;

/**
 * The menus of {@code /company order}: the orders of a company and the company view of one order.
 */
public final class CompanyOrderViews {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public CompanyOrderViews(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Shows the company view of an order to a member.
     *
     * @param player  viewing player
     * @param company company of the player, whose level decides the tax shown
     * @param member  the player as member of the company
     * @param order   order to show
     */
    public void renderOrder(Player player, ISimpleCompany company, CompanyMember member, FullOrder order) {
        var services = deps.services();
        var menus = services.menus();
        var configuration = deps.configuration();
        var taxPercent = configuration.companySettings().taxPercent(company.level());
        var component = order.companyDetailInfo(
                member,
                configuration,
                services.economy(),
                taxPercent,
                services.deliveryService().matcher());
        var composer = MessageComposer.create().text(component);
        menus.show(player, composer);
    }

    /**
     * Shows the orders a company works on.
     *
     * @param company company of the player
     * @param player  viewing player
     */
    public void showOrders(ISimpleCompany company, Player player) {
        showOrders(company, player, () -> {});
    }

    /**
     * Shows the orders a company works on and runs an action once they are shown.
     *
     * @param company company of the player
     * @param player  viewing player
     * @param after   runs after the orders were shown
     */
    public void showOrders(ISimpleCompany company, Player player, Runnable after) {
        var services = deps.services();
        var orderData = deps.orderData();
        orderData
                .retrieveOrdersByCompany(company, OrderState.CLAIMED, OrderState.CLAIMED)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Collections.emptyList();
                })
                .thenApply(orders -> orderData
                        .retrieveFullOrders(orders)
                        .asFuture()
                        .exceptionally(err -> {
                            deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                            return Collections.emptyList();
                        })
                        .join())
                .thenAccept(fullOrders -> {
                    var taxPercent = deps.configuration().companySettings().taxPercent(company.level());
                    var menus = services.menus();
                    var builder = MessageComposer.create()
                            .text("<heading>")
                            .localeCode("company.order.list.orders")
                            .text(": <click:run_command:/company order search query><show>[")
                            .localeCode("words.search")
                            .text("]</click>")
                            .newLine();
                    for (var order : fullOrders) {
                        builder.text(order.companyShortInfo(services.economy(), taxPercent, false))
                                .newLine();
                    }
                    menus.show(player, builder);
                    after.run();
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }
}
