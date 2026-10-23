/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.CompanyOrderIds;
import de.eldoria.companies.commands.suggest.CompanyOrderSources;
import de.eldoria.companies.commands.suggest.OrderLines;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.orders.DeliveryResult;
import de.eldoria.companies.orders.DeliveryService;
import de.eldoria.companies.util.Threading;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Literal;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.IntArg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * {@code /company order deliver <id> <line> <amount|max>} and {@code /company order deliver <id> all}: delivers items
 * from the inventory and from a shulker box in the main hand to an order the company works on.
 * <p>
 * Lines are numbered from one. A delivery to one line takes up to the given amount, {@code max} takes what the line
 * misses, and {@code all} takes every matching item for every line.
 */
@Subcommand("deliver")
@PlayerOnly
public final class Deliver {
    private final CommandDependencies deps;
    private final CompanyOrderViews views;

    /**
     * @param deps  what the commands need
     * @param views the menus of the company orders
     */
    public Deliver(CommandDependencies deps, CompanyOrderViews views) {
        this.deps = deps;
        this.views = views;
    }

    /**
     * Delivers an amount of items to one line.
     *
     * @param sender sender of the command
     * @param id     id of the order
     * @param line   line of the order, from one
     * @param amount the most items to deliver
     */
    @Executes
    public void deliver(
            CommandSender sender,
            @CompanyOrderIds int id,
            @OrderLines @IntArg(min = 1) int line,
            @IntArg(min = 1) int amount) {
        deliverLine(sender, id, line, amount);
    }

    /**
     * Delivers as many items to one line as it misses.
     *
     * @param sender sender of the command
     * @param id     id of the order
     * @param line   line of the order, from one
     * @param max    the literal {@code max}
     */
    @Executes
    public void deliverMax(
            CommandSender sender,
            @CompanyOrderIds int id,
            @OrderLines @IntArg(min = 1) int line,
            @Literal("max") String max) {
        deliverLine(sender, id, line, Integer.MAX_VALUE);
    }

    /**
     * Delivers every matching item to every line.
     *
     * @param sender sender of the command
     * @param id     id of the order
     * @param all    the literal {@code all}
     */
    @Executes
    public void deliverAll(CommandSender sender, @CompanyOrderIds int id, @Literal("all") String all) {
        var player = Senders.player(sender);
        var service = deps.services().deliveryService();
        start(player, id, (company, order) -> Optional.of(service.deliverAll(player, company, order)));
    }

    private void deliverLine(CommandSender sender, int id, int line, int amount) {
        var player = Senders.player(sender);
        var service = deps.services().deliveryService();
        start(player, id, (company, order) -> {
            if (order.line(line - 1).isEmpty()) {
                deps.messages().sendErrorActionBar(player, "error.unknownLine");
                return Optional.empty();
            }
            return Optional.of(service.deliver(player, company, order, line - 1, amount));
        });
    }

    /**
     * Loads the order of the company and runs the delivery on the main thread.
     *
     * @param player   delivering player
     * @param id       id of the order
     * @param delivery takes the items; empty when it refused
     */
    private void start(Player player, int id, Delivery delivery) {
        var messages = deps.messages();
        var orderData = deps.orderData();
        deps.companyData()
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(company -> {
                    if (company.isEmpty()) {
                        messages.sendErrorActionBar(player, "error.noMember");
                        return;
                    }
                    var optOrder = orderData
                            .retrieveCompanyOrderById(id, company.get().id())
                            .join();
                    if (optOrder == null || optOrder.isEmpty()) {
                        messages.sendErrorActionBar(player, "error.unknownOrder");
                        return;
                    }
                    if (optOrder.get().state() != OrderState.CLAIMED) {
                        messages.sendErrorActionBar(player, "error.orderNotActive");
                        return;
                    }
                    var order = orderData.retrieveFullOrder(optOrder.get()).join();
                    Threading.mainThread(deps.plugin())
                            .execute(() -> deliverOnMainThread(player, company.get(), order, delivery));
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    private void deliverOnMainThread(Player player, CompanyProfile company, FullOrder order, Delivery delivery) {
        if (!player.isOnline()) return;
        delivery.run(company, order).ifPresent(result -> result.thenAccept(delivered -> {
                    CompanyOrderSources.invalidate(player.getUniqueId());
                    if (delivered.orderDone()) {
                        deps.services().menus().close(player);
                        return;
                    }
                    views.renderOrder(player, company, company.member(player).get(), delivered.order());
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                }));
    }

    /**
     * One kind of delivery, run by {@link DeliveryService} on the main thread.
     */
    @FunctionalInterface
    private interface Delivery {
        /**
         * @param company company of the worker
         * @param order   order to deliver to
         * @return the running delivery, or empty when it was refused
         */
        Optional<CompletableFuture<DeliveryResult>> run(CompanyProfile company, FullOrder order);
    }
}
