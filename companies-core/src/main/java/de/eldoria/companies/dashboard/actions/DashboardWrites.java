/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.orders.CompanyOrderService;
import de.eldoria.companies.orders.OrderAcceptService;
import de.eldoria.companies.orders.OwnerOrderService;
import de.eldoria.companies.services.ActionResult.Done;
import de.eldoria.companies.services.ActionResult.Refused;
import de.eldoria.companies.services.notifications.Inbox;
import de.eldoria.companies.services.notifications.NotificationCategory;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Performs the actions of the dashboard that change something: accepting and aborting orders for the company,
 * cancelling, extending and raising own orders, and reading, clearing and muting the inbox.
 * <p>
 * Every action goes through the same service the command uses, with the session's player as actor, so the company
 * permissions, order states and limits are checked exactly as in game. A refusal carries the service's locale key as
 * reason and its text as message. Cancelling and raising answer a quote instead when the arguments hold
 * {@code "quote": true}: cancelling says whether the order belongs to a repeating order, raising names the fee and the
 * total, and nothing is changed. The methods block on the database and the economy and are called off the main thread.
 */
public final class DashboardWrites {
    /**
     * The actions this version performs.
     */
    public static final Set<String> ACTIONS = Set.of(
            Actions.ORDERS_ACCEPT,
            Actions.ORDERS_ABORT,
            Actions.ORDERS_OWN_CANCEL,
            Actions.ORDERS_OWN_EXTEND,
            Actions.ORDERS_OWN_RAISE,
            Actions.INBOX_READ_ALL,
            Actions.INBOX_CLEAR,
            Actions.INBOX_MUTE);

    private final Sources sources;
    private final JsonMapper mapper = Protocol.mapper();

    /**
     * @param sources what the actions are performed with
     */
    public DashboardWrites(Sources sources) {
        this.sources = sources;
    }

    /**
     * Whether this version performs an action.
     *
     * @param action name of the action
     * @return true when it is one of {@link #ACTIONS}
     */
    public static boolean performs(String action) {
        return ACTIONS.contains(action);
    }

    /**
     * Performs an action of a session.
     *
     * @param request the action, one of {@link #ACTIONS}
     * @param session the session it came from, checked by the caller
     * @return the result
     */
    public ActionResult perform(ActionRequest request, StoredSession session) {
        var args = request.args() == null ? mapper.createObjectNode() : request.args();
        var player = session.player();
        return switch (request.name()) {
            case Actions.ORDERS_ACCEPT -> accept(request, player, order(args));
            case Actions.ORDERS_ABORT -> abort(request, player, order(args));
            case Actions.ORDERS_OWN_CANCEL -> cancel(request, player, args);
            case Actions.ORDERS_OWN_EXTEND -> extend(request, player, order(args));
            case Actions.ORDERS_OWN_RAISE -> raise(request, player, args);
            case Actions.INBOX_READ_ALL ->
                data(
                        request,
                        Map.of(
                                "marked",
                                sources.inbox()
                                        .readAll(player, LocalDateTime.now(sources.clock()))
                                        .join()));
            case Actions.INBOX_CLEAR ->
                data(
                        request,
                        Map.of("deleted", sources.inbox().clearRead(player).join()));
            case Actions.INBOX_MUTE -> mute(request, player, args);
            default -> refused(request, "dashboard.unsupported");
        };
    }

    private ActionResult accept(ActionRequest request, UUID player, int order) {
        Optional<CompanyProfile> company = sources.companyOf().apply(player).join();
        if (company.isEmpty()) return refused(request, "error.noMember");
        return switch (sources.accept().accept(player, company.get(), order).join()) {
            case OrderAcceptService.Result.Accepted accepted ->
                data(request, Map.of("order", accepted.order().id()));
            case OrderAcceptService.Result.Refused(var failure) -> refused(request, failure);
        };
    }

    private ActionResult abort(ActionRequest request, UUID player, int order) {
        return switch (sources.companyOrders().abort(player, order).join()) {
            case Done<CompanyOrderService.Aborted>(var aborted) ->
                data(request, Map.of("order", aborted.order().id()));
            case Refused<CompanyOrderService.Aborted>(var failure) -> refused(request, failure);
        };
    }

    private ActionResult cancel(ActionRequest request, UUID player, JsonNode args) {
        var order = order(args);
        if (quote(args)) {
            return switch (sources.ownerOrders().cancellable(player, order).join()) {
                case Done<OwnerOrderService.Cancellable>(var cancellable) ->
                    data(
                            request,
                            Map.of(
                                    "order", cancellable.order().id(),
                                    "repeating", cancellable.activeSeries().isPresent()));
                case Refused<OwnerOrderService.Cancellable>(var failure) -> refused(request, failure);
            };
        }
        return switch (sources.ownerOrders().cancel(player, order).join()) {
            case Done<?>(var ignored) -> data(request, Map.of("order", order));
            case Refused<?>(var failure) -> refused(request, failure);
        };
    }

    private ActionResult extend(ActionRequest request, UUID player, int order) {
        return switch (sources.ownerOrders().extend(player, order).join()) {
            case Done<OwnerOrderService.Extended>(var extended) ->
                data(
                        request,
                        Map.of(
                                "order", extended.order().id(),
                                "left", extended.left(),
                                "refundInSeconds", extended.refundIn().toSeconds()));
            case Refused<OwnerOrderService.Extended>(var failure) -> refused(request, failure);
        };
    }

    private ActionResult raise(ActionRequest request, UUID player, JsonNode args) {
        var order = order(args);
        var increase = args.path("increase").asDouble(0);
        if (!(increase > 0) || !Double.isFinite(increase)) return refused(request, "error.invalidNumber");
        var result = quote(args)
                ? sources.ownerOrders().raiseQuote(player, order, increase).join()
                : sources.ownerOrders().raise(player, order, increase).join();
        return switch (result) {
            case Done<OwnerOrderService.Raise>(var raise) ->
                data(
                        request,
                        Map.of(
                                "order", order,
                                "increase", raise.increase(),
                                "fee", raise.fee(),
                                "total", raise.total()));
            case Refused<OwnerOrderService.Raise>(var failure) -> refused(request, failure);
        };
    }

    private ActionResult mute(ActionRequest request, UUID player, JsonNode args) {
        var name = args.path("category").asString("").toUpperCase(Locale.ROOT);
        NotificationCategory category;
        try {
            category = NotificationCategory.valueOf(name);
        } catch (IllegalArgumentException e) {
            return refused(request, "dashboard.unknownCategory");
        }
        if (!category.mutable()) return refused(request, "dashboard.unknownCategory");
        var mute = args.path("mute").asBoolean(true);
        var mask = sources.inbox().mute(player, category, mute).join();
        return data(request, Map.of("category", category.name(), "muted", (mask & category.bit()) != 0, "mask", mask));
    }

    private static int order(JsonNode args) {
        return args.path("order").asInt(0);
    }

    private static boolean quote(JsonNode args) {
        return args.path("quote").asBoolean(false);
    }

    private ActionResult data(ActionRequest request, Object value) {
        return new ActionResult(request.id(), Protocol.VERSION, true, null, null, mapper.valueToTree(value));
    }

    private ActionResult refused(ActionRequest request, CommandFailure failure) {
        return new ActionResult(
                request.id(),
                Protocol.VERSION,
                false,
                failure.localeKey(),
                sources.render().apply(failure),
                null);
    }

    private ActionResult refused(ActionRequest request, String reason) {
        return refused(request, CommandFailure.of(reason));
    }

    /**
     * What the actions are performed with.
     *
     * @param companyOf     the company a player is a member of
     * @param accept        accepts orders for a company
     * @param companyOrders aborts the company's orders
     * @param ownerOrders   cancels, extends and raises own orders
     * @param inbox         the inboxes
     * @param render        renders a refusal as plain text, with its values
     * @param clock         the clock
     */
    public record Sources(
            Function<UUID, CompletableFuture<Optional<CompanyProfile>>> companyOf,
            OrderAcceptService accept,
            CompanyOrderService companyOrders,
            OwnerOrderService ownerOrders,
            Inbox inbox,
            Function<CommandFailure, String> render,
            Clock clock) {}
}
