/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.commands.suggest.CompanyOrderSources;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.order.OrderCanceledEvent;
import de.eldoria.companies.services.ActionResult;
import de.eldoria.companies.util.AsyncEvents;
import de.eldoria.companies.util.Threading;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * What members do with the orders their company works on: giving an order back.
 * <p>
 * The member is given by id and does not need to be online. They need {@link CompanyPermission#MANAGE_ORDERS}. An
 * aborted order is open for other companies again, the delivered items go back to their workers as item returns, and
 * {@link OrderCanceledEvent} fires. Everything runs on the executor, off the main thread.
 */
public final class CompanyOrderService {
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final OrderOutcomes outcomes;
    private final Consumer<Event> events;
    private final Consumer<UUID> ordersChanged;
    private final Function<UUID, OfflinePlayer> players;
    private final Executor executor;

    /**
     * @param companyData   company repository
     * @param orderData     order repository
     * @param outcomes      ends aborted orders and returns the delivered items
     * @param events        fires the event of an abort, off the main thread
     * @param ordersChanged drops what is cached about the company orders of a member
     * @param players       looks up a player by id
     * @param executor      runs the actions
     */
    public CompanyOrderService(
            ACompanyData companyData,
            AOrderData orderData,
            OrderOutcomes outcomes,
            Consumer<Event> events,
            Consumer<UUID> ordersChanged,
            Function<UUID, OfflinePlayer> players,
            Executor executor) {
        this.companyData = companyData;
        this.orderData = orderData;
        this.outcomes = outcomes;
        this.events = events;
        this.ordersChanged = ordersChanged;
        this.players = players;
        this.executor = executor;
    }

    /**
     * The service of a running plugin, firing Bukkit events.
     *
     * @param plugin      the plugin
     * @param companyData company repository
     * @param orderData   order repository
     * @param outcomes    ends aborted orders
     * @return the service
     */
    public static CompanyOrderService create(
            Plugin plugin, ACompanyData companyData, AOrderData orderData, OrderOutcomes outcomes) {
        return new CompanyOrderService(
                companyData,
                orderData,
                outcomes,
                event -> AsyncEvents.fire(plugin, event),
                CompanyOrderSources::invalidate,
                plugin.getServer()::getOfflinePlayer,
                Threading.VIRTUAL);
    }

    /**
     * Checks whether the member could give back an order of their company, without giving it back.
     *
     * @param actor member who wants to abort the order
     * @param id    id of the order
     * @return the company and the order, or why it can not be aborted
     */
    public CompletableFuture<ActionResult<Aborted>> abortable(UUID actor, int id) {
        return CompletableFuture.supplyAsync(() -> check(actor, id), executor);
    }

    /**
     * Gives back an order the company of the member works on.
     *
     * @param actor member who aborts the order
     * @param id    id of the order
     * @return the company and the order before the abort, or why it was not aborted
     */
    public CompletableFuture<ActionResult<Aborted>> abort(UUID actor, int id) {
        return CompletableFuture.supplyAsync(
                () -> switch (check(actor, id)) {
                    case ActionResult.Refused<Aborted> refused -> refused;
                    case ActionResult.Done<Aborted>(var aborted) -> abortNow(actor, aborted);
                },
                executor);
    }

    private ActionResult<Aborted> abortNow(UUID actor, Aborted aborted) {
        var returned = outcomes.abort(aborted.order()).join();
        if (returned == null || returned.isEmpty()) return ActionResult.refusedActionBar("error.orderNotActive");
        ordersChanged.accept(actor);
        events.accept(new OrderCanceledEvent(aborted.order(), aborted.company()));
        return ActionResult.done(aborted);
    }

    private ActionResult<Aborted> check(UUID actor, int id) {
        var optCompany = companyData
                .retrievePlayerCompanyProfile(players.apply(actor))
                .asFuture()
                .join();
        if (optCompany.isEmpty()) return ActionResult.refusedActionBar("error.noMember");
        var company = optCompany.get();
        var optOrder =
                Optional.ofNullable(orderData.retrieveOrderById(id).join()).flatMap(found -> found);
        if (optOrder.isEmpty()) return ActionResult.refusedActionBar("error.unknownOrder");
        var order = optOrder.get();
        if (order.company() != company.id()) return ActionResult.refusedActionBar("error.orderNotOwned");
        if (order.state() != OrderState.CLAIMED) return ActionResult.refusedActionBar("error.orderNotActive");
        var mayManage = company.member(actor)
                .map(member -> member.hasPermissions(CompanyPermission.MANAGE_ORDERS))
                .orElse(false);
        if (!mayManage) return ActionResult.refusedActionBar("error.permission.cancelOrder");
        return ActionResult.done(new Aborted(company, order));
    }

    /**
     * An order a company gives back.
     *
     * @param company the company
     * @param order   the order as it was before the abort
     */
    public record Aborted(CompanyProfile company, SimpleOrder order) {}
}
