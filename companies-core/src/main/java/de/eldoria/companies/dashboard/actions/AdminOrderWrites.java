/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.dashboard.views.Payloads;
import de.eldoria.companies.dashboard.views.Views;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.services.AdminOrderLookup;
import de.eldoria.companies.services.AdminOrderService;
import de.eldoria.companies.util.AdminLog;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import de.eldoria.companies.web.api.session.PlayerRef;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The dashboard's admin tools for orders, as {@code /companyadmin order} offers them: an order's details by id, the
 * orders of a player or a company page by page, and taking a claimed order from its company, deleting an order with or
 * without a refund, marking a claimed order delivered and giving an order more time. The admin needs
 * {@code companies.admin.orders}, checked against the permissions remembered with the session as every dashboard
 * action is. Every action is written to the admin log under the admin's name with {@code @dashboard}, one word, so
 * the line splits on spaces as the command's lines do.
 * <p>
 * An order is named by {@code order}; a search by {@code player} or {@code company}, by name, and {@code page}.
 * Cancelling takes {@code refund}, extending {@code hours} (1 to 8760). The details carry what cancelling and
 * delivering would move, so the confirmation can name it. The methods block and are called off the main thread.
 */
public final class AdminOrderWrites {
    /**
     * The actions performed here.
     */
    public static final Set<String> ACTIONS = Set.of(
            Actions.ADMIN_ORDERS_INFO,
            Actions.ADMIN_ORDERS_SEARCH,
            Actions.ADMIN_ORDERS_UNCLAIM,
            Actions.ADMIN_ORDERS_CANCEL,
            Actions.ADMIN_ORDERS_DELIVER,
            Actions.ADMIN_ORDERS_EXTEND);

    /**
     * Orders on one page of a search.
     */
    public static final int PAGE_SIZE = 10;

    private static final int MAX_HOURS = 8760;
    private static final int MAX_PAGE = 100_000;
    private static final String INVALID = "dashboard.invalid";
    private static final String UNKNOWN_ORDER = "error.unknownOrder";

    private final Sources sources;
    private final Answers answers;

    /**
     * @param sources what the orders are looked up and changed with
     */
    public AdminOrderWrites(Sources sources) {
        this.sources = sources;
        answers = new Answers(sources.render());
    }

    /**
     * Performs an admin action on orders.
     *
     * @param request the action, one of {@link #ACTIONS}
     * @param session the admin's session
     * @param args    the arguments
     * @return the result
     */
    ActionResult perform(ActionRequest request, StoredSession session, JsonNode args) {
        if (!session.permissions().contains(Permission.Admin.ORDERS)) {
            return answers.refused(request, "companyadmin.error.permission");
        }
        var admin = session.playerName() + "@dashboard";
        if (request.name().equals(Actions.ADMIN_ORDERS_SEARCH)) return search(request, admin, args);
        var id = args.path("order").asInt(0);
        if (id <= 0) return answers.refused(request, UNKNOWN_ORDER);
        return switch (request.name()) {
            case Actions.ADMIN_ORDERS_INFO -> {
                log(admin, "order info").with("id", id).write();
                yield info(request, id);
            }
            case Actions.ADMIN_ORDERS_UNCLAIM ->
                done(request, sources.service().unclaim(id), unclaimed -> {
                    log(admin, "order unclaim", unclaimed.order())
                            .with("workers", unclaimed.workers().size())
                            .write();
                    return Map.of("order", id, "workers", unclaimed.workers().size());
                });
            case Actions.ADMIN_ORDERS_CANCEL -> cancel(request, admin, id, args);
            case Actions.ADMIN_ORDERS_DELIVER ->
                done(request, sources.service().deliver(id), delivered -> {
                    var order = delivered.completion().order();
                    log(admin, "order deliver", order)
                            .with("delivered", order.delivered())
                            .with("amount", order.amount())
                            .with(
                                    "paid_workers",
                                    delivered.completion().payouts().size())
                            .with("refunded", delivered.refund())
                            .write();
                    return Map.of("order", id, "refund", delivered.refund());
                });
            case Actions.ADMIN_ORDERS_EXTEND -> extend(request, admin, id, args);
            default -> answers.refused(request, "dashboard.unsupported");
        };
    }

    private ActionResult info(ActionRequest request, int id) {
        return sources.lookup()
                .details(id)
                .map(details -> answers.data(request, view(details)))
                .orElseGet(() -> answers.refused(request, UNKNOWN_ORDER));
    }

    private ActionResult search(ActionRequest request, String admin, JsonNode args) {
        var page = Math.clamp(args.path("page").asInt(1), 1, MAX_PAGE);
        var player = args.path("player").asString("").strip();
        var company = args.path("company").asString("").strip();
        if (!player.isEmpty()) {
            var found = sources.player().apply(player);
            if (found.isEmpty()) return answers.refused(request, "error.unknownPlayer");
            log(admin, "order search").with("player", found.get().name()).write();
            return page(request, found.get().name(), found.get().uuid(), null, page);
        }
        if (!company.isEmpty()) {
            Optional<SimpleCompany> found = sources.company().apply(company).join();
            if (found.isEmpty()) return answers.refused(request, "error.unknownCompany");
            log(admin, "order search")
                    .with("company", found.get().name())
                    .with("company_id", found.get().id())
                    .write();
            return page(request, found.get().name(), null, found.get().id(), page);
        }
        return answers.refused(request, INVALID);
    }

    private ActionResult page(ActionRequest request, String target, UUID owner, Integer company, int page) {
        var rows = sources.lookup().page(owner, company, page, PAGE_SIZE);
        return answers.data(
                request,
                new Payloads.AdminOrderPage(
                        target,
                        page,
                        rows.size() >= PAGE_SIZE,
                        rows.stream()
                                .map(row -> new Payloads.AdminOrderRow(
                                        row.order().id(),
                                        row.order().name(),
                                        row.order().state().name(),
                                        row.ownerName(),
                                        row.claimantName().orElse(null),
                                        instant(row.order().created())))
                                .toList()));
    }

    private ActionResult cancel(ActionRequest request, String admin, int id, JsonNode args) {
        if (!args.path("refund").isBoolean()) return answers.refused(request, INVALID);
        var refund = args.path("refund").asBoolean();
        return done(request, sources.service().cancel(id, refund), cancelled -> {
            var money = cancelled.money();
            log(admin, "order cancel", cancelled.order())
                    .with("refund", refund)
                    .with("refunded", money.refund())
                    .with("collected", money.collected())
                    .with("workers", cancelled.workers().size())
                    .write();
            return Map.of("order", id, "refunded", money.refund(), "collected", money.collected());
        });
    }

    private ActionResult extend(ActionRequest request, String admin, int id, JsonNode args) {
        var hours = args.path("hours");
        if (!hours.isIntegralNumber() || !hours.canConvertToInt() || hours.asInt() < 1 || hours.asInt() > MAX_HOURS) {
            return answers.refused(request, "companyadmin.order.error.invalidHours");
        }
        return done(request, sources.service().extend(id, hours.asInt()), extended -> {
            log(admin, "order extend", extended.order())
                    .with("hours", hours.asInt())
                    .write();
            return Map.of("order", id, "hours", hours.asInt());
        });
    }

    private Payloads.AdminOrder view(AdminOrderLookup.Details details) {
        var order = details.order();
        var lines = order.contents().stream()
                .map(content -> {
                    Map<String, Integer> workers = new LinkedHashMap<>();
                    content.workerAmount()
                            .forEach((worker, amount) ->
                                    workers.put(details.names().getOrDefault(worker, worker.toString()), amount));
                    return new Payloads.AdminOrderLine(
                            Views.item(content.stack(), content.amount()),
                            content.delivered(),
                            content.price(),
                            workers);
                })
                .toList();
        var cancelRefund = AdminOrderService.cancelMoney(order, true);
        var cancelKeep = AdminOrderService.cancelMoney(order, false);
        var deadline = details.deadline();
        return new Payloads.AdminOrder(
                order.id(),
                order.name(),
                order.state().name(),
                new PlayerRef(order.owner(), details.ownerName()),
                details.companyName().orElse(null),
                order.soloWorker()
                        .map(worker -> details.names().getOrDefault(worker, worker.toString()))
                        .orElse(null),
                instant(order.created()),
                deadline.map(found -> instant(found.at())).orElse(null),
                deadline.map(AdminOrderLookup.Deadline::removal).orElse(false),
                order.price(),
                order.fee(),
                lines,
                cancelRefund.refund(),
                cancelKeep.collected(),
                AdminOrderService.undeliveredRefund(order));
    }

    private <T> ActionResult done(
            ActionRequest request,
            CompletableFuture<AdminOrderService.Result<T>> running,
            Function<T, Map<String, Object>> answer) {
        return switch (running.join()) {
            case AdminOrderService.Result.Done<T> performed -> answers.data(request, answer.apply(performed.value()));
            case AdminOrderService.Result.Refused<T> refused ->
                answers.refused(request, refused.refusal().key());
        };
    }

    private Instant instant(LocalDateTime time) {
        return sources.views().instant(time);
    }

    private AdminLog.Entry log(String admin, String action) {
        return sources.log().entry(admin, action);
    }

    private AdminLog.Entry log(String admin, String action, SimpleOrder order) {
        return log(admin, action)
                .with("id", order.id())
                .with("order", order.name())
                .with("owner", sources.names().apply(order.owner()));
    }

    /**
     * What the orders are looked up and changed with.
     *
     * @param lookup  looks orders up with their names
     * @param service changes any order as an admin
     * @param player  a player known to the server by name, with id and exact name
     * @param company a company by name
     * @param names   the name of a player by id
     * @param views   turns stored times into instants
     * @param log     the admin log
     * @param render  renders a refusal as plain text
     */
    public record Sources(
            AdminOrderLookup lookup,
            AdminOrderService service,
            Function<String, Optional<PlayerRef>> player,
            Function<String, CompletableFuture<Optional<SimpleCompany>>> company,
            Function<UUID, String> names,
            Views views,
            AdminLog log,
            Function<CommandFailure, String> render) {}
}
