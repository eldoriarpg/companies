/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.order.OrderAcceptEvent;
import de.eldoria.companies.events.order.PreOrderAcceptEvent;
import de.eldoria.eldoutilities.messages.Replacement;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Accepts orders for companies, from the command and from the order menus alike.
 * <p>
 * The member needs {@link CompanyPermission#MANAGE_ORDERS}. The company must be below the order limit of its level, the
 * order must be unclaimed, fit the order size of the level and must not belong to a member unless member orders are
 * allowed. Then {@link PreOrderAcceptEvent} lets integrations refuse the order with a reason; the claim itself only
 * succeeds while the order is still unclaimed and is followed by {@link OrderAcceptEvent}. Everything runs on the
 * executor, off the main thread.
 */
public final class OrderAcceptService {
    private final AOrderData orderData;
    private final Supplier<CompanySettings> settings;
    private final Consumer<Event> events;
    private final Executor executor;

    /**
     * @param orderData order repository
     * @param settings  the current company settings
     * @param events    fires the events of the acceptance
     * @param executor  runs the acceptance off the main thread
     */
    public OrderAcceptService(
            AOrderData orderData, Supplier<CompanySettings> settings, Consumer<Event> events, Executor executor) {
        this.orderData = orderData;
        this.settings = settings;
        this.events = events;
        this.executor = executor;
    }

    /**
     * Accepts an order for the company of a member.
     *
     * @param player  accepting member
     * @param company company of the member, with its members
     * @param id      id of the order
     * @return the claimed order, or why it was not accepted
     */
    public CompletableFuture<Result> accept(Player player, CompanyProfile company, int id) {
        return CompletableFuture.supplyAsync(() -> acceptNow(player, company, id), executor);
    }

    private Result acceptNow(Player player, CompanyProfile company, int id) {
        var member = company.member(player);
        if (member.isEmpty() || !member.get().hasPermission(CompanyPermission.MANAGE_ORDERS)) {
            return refused("error.permission.acceptOrder");
        }
        var companySettings = settings.get();
        var levelSettings = companySettings.levelSettings(company.level());
        var count = orderData.retrieveCompanyOrderCount(company).join();
        if (count == null) return refused("error.couldNotClaim");
        if (count >= levelSettings.maxOrders()) return refused("error.orderLimit");

        var found = orderData.retrieveOrderById(id).join();
        if (found == null || found.isEmpty()) return refused("error.unknownOrder");
        var order = found.get();
        if (order.state() != OrderState.UNCLAIMED) return refused("error.orderNotClaimable");

        var fullOrder = orderData.retrieveFullOrder(order).join();
        if (fullOrder == null) return refused("error.unknownOrder");
        if (levelSettings.exceedsOrderSize(fullOrder.amount())) {
            return new Result.Refused(CommandFailure.actionBar(
                    "error.orderTooLarge", Replacement.create("max", levelSettings.maxOrderSize())));
        }
        if (!company.mayAcceptOrderOf(order.owner(), companySettings.allowMemberOrders())) {
            return refused("error.ownMemberOrder");
        }

        var event = new PreOrderAcceptEvent(fullOrder, company, player);
        events.accept(event);
        if (event.isCancelled()) return new Result.Refused(cancelled(event));

        var claimed = orderData
                .submitOrderClaim(company, order, levelSettings.deliveryHoursBonus())
                .join();
        if (!Boolean.TRUE.equals(claimed)) return refused("error.couldNotClaim");
        events.accept(new OrderAcceptEvent(order, company));
        return new Result.Accepted(claimedOrder(order));
    }

    private FullOrder claimedOrder(SimpleOrder order) {
        var refreshed = Optional.ofNullable(
                        orderData.retrieveOrderById(order.id()).join())
                .flatMap(found -> found)
                .orElse(order);
        return orderData.retrieveFullOrder(refreshed).join();
    }

    private static CommandFailure cancelled(PreOrderAcceptEvent event) {
        return event.cancelReason()
                .map(reason -> CommandFailure.of("error.cancelledByIntegration", reason))
                .orElseGet(() -> CommandFailure.actionBar("error.cancelledByIntegration"));
    }

    private static Result refused(String key) {
        return new Result.Refused(CommandFailure.actionBar(key));
    }

    /**
     * The outcome of an acceptance.
     */
    public sealed interface Result {
        /**
         * The order is claimed by the company.
         *
         * @param order the claimed order
         */
        record Accepted(FullOrder order) implements Result {}

        /**
         * The order was not accepted.
         *
         * @param failure what the member is told
         */
        record Refused(CommandFailure failure) implements Result {}
    }
}
