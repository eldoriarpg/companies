/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Accepts orders for companies, from the command and from the order menus alike.
 * <p>
 * The member needs {@link CompanyPermission#MANAGE_ORDERS}. The company must be below the order limit of its level, the
 * order must be unclaimed, fit the order size of the level and must not belong to a member unless member orders are
 * allowed. Then {@link PreOrderAcceptEvent} lets integrations refuse the order with a reason; the claim itself only
 * succeeds while the order is still unclaimed and is followed by {@link OrderAcceptEvent}. Everything runs on the
 * executor, off the main thread.
 * <p>
 * The member may be given by id and does not need to be online. {@link PreOrderAcceptEvent} carries an online
 * {@link Player}, so it fires only while the member is online on this server; a member who is offline here accepts
 * without it.
 */
public final class OrderAcceptService {
    private final AOrderData orderData;
    private final Supplier<CompanySettings> settings;
    private final Consumer<Event> events;
    private final Function<UUID, Optional<Player>> online;
    private final Executor executor;
    private final Limit limit;

    /**
     * @param orderData order repository
     * @param settings  the current company settings
     * @param events    fires the events of the acceptance
     * @param executor  runs the acceptance off the main thread
     */
    public OrderAcceptService(
            AOrderData orderData, Supplier<CompanySettings> settings, Consumer<Event> events, Executor executor) {
        this(orderData, settings, events, uuid -> Optional.ofNullable(Bukkit.getPlayer(uuid)), executor);
    }

    /**
     * @param orderData order repository
     * @param settings  the current company settings
     * @param events    fires the events of the acceptance
     * @param online    looks up a player online on this server by id
     * @param executor  runs the acceptance off the main thread
     */
    public OrderAcceptService(
            AOrderData orderData,
            Supplier<CompanySettings> settings,
            Consumer<Event> events,
            Function<UUID, Optional<Player>> online,
            Executor executor) {
        this(orderData, settings, events, online, executor, Limit.NONE);
    }

    /**
     * @param orderData order repository
     * @param settings  the current company settings
     * @param events    fires the events of the acceptance
     * @param online    looks up a player online on this server by id
     * @param executor  runs the acceptance off the main thread
     * @param limit     refuses orders a company may not take for reasons of their own, such as a daily cap
     */
    public OrderAcceptService(
            AOrderData orderData,
            Supplier<CompanySettings> settings,
            Consumer<Event> events,
            Function<UUID, Optional<Player>> online,
            Executor executor,
            Limit limit) {
        this.orderData = orderData;
        this.settings = settings;
        this.events = events;
        this.online = online;
        this.executor = executor;
        this.limit = limit;
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
        return CompletableFuture.supplyAsync(
                () -> acceptNow(player.getUniqueId(), Optional.of(player), company, id), executor);
    }

    /**
     * Accepts an order for the company of a member, who does not need to be online.
     *
     * @param actor   id of the accepting member
     * @param company company of the member, with its members
     * @param id      id of the order
     * @return the claimed order, or why it was not accepted
     */
    public CompletableFuture<Result> accept(UUID actor, CompanyProfile company, int id) {
        return CompletableFuture.supplyAsync(() -> acceptNow(actor, online.apply(actor), company, id), executor);
    }

    private Result acceptNow(UUID actor, Optional<Player> player, CompanyProfile company, int id) {
        var member = company.member(actor);
        if (member.isEmpty() || !member.get().hasPermission(CompanyPermission.MANAGE_ORDERS)) {
            return refused("error.permission.acceptOrder");
        }
        var companySettings = settings.get();
        var levelSettings = companySettings.levelSettings(company.level());
        var count = orderData.retrieveCompanySlotCount(company).join();
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
        var limited = limit.refusal(company, order);
        if (limited.isPresent()) return new Result.Refused(limited.get());

        var refusal = player.map(acting -> new PreOrderAcceptEvent(fullOrder, company, acting))
                .filter(this::cancelledBy)
                .map(OrderAcceptService::cancelled);
        if (refusal.isPresent()) return new Result.Refused(refusal.get());

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

    private boolean cancelledBy(PreOrderAcceptEvent event) {
        events.accept(event);
        return event.isCancelled();
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
     * Refuses orders a company may not take for reasons of their own.
     */
    @FunctionalInterface
    public interface Limit {
        /**
         * No limit.
         */
        Limit NONE = (company, order) -> Optional.empty();

        /**
         * @param company the company accepting
         * @param order   the order
         * @return why the company may not take it, empty when it may
         */
        Optional<CommandFailure> refusal(CompanyProfile company, SimpleOrder order);
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
