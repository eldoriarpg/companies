/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.orders.DeliveryResult;
import de.eldoria.companies.orders.DeliveryService;
import de.eldoria.companies.orders.OrderReceiveService;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import org.bukkit.entity.Player;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Performs the dashboard actions that move items, with the player's inventory: delivering to an order the player's
 * company works on (one line, up to an amount or what it misses, or every line), receiving a delivered own order, and
 * collecting the items waiting for the player. They run exactly as the commands do, on the main thread of the node
 * the player is online on; the gateway routes them there, and a node the player is not on refuses them with
 * {@code dashboard.notOnline}.
 * <p>
 * Lines are numbered from one, as in game. Delivering answers how many items were delivered and whether the order is
 * done, receiving how many items the order holds and how many wait to be collected, collecting how many items were
 * handed out and how many are still waiting. The methods block and are called off the main thread.
 */
public final class ItemWrites {
    /**
     * The actions performed here.
     */
    public static final Set<String> ACTIONS = Set.of(
            Actions.ORDERS_DELIVER_LINE,
            Actions.ORDERS_DELIVER_ALL,
            Actions.ORDERS_DELIVER_RECEIVE,
            Actions.ORDERS_DELIVER_COLLECT);

    private static final String NOT_ONLINE = "dashboard.notOnline";

    private final Sources sources;
    private final Answers answers;

    /**
     * @param sources what the items are moved with
     */
    public ItemWrites(Sources sources) {
        this.sources = sources;
        answers = new Answers(sources.render());
    }

    /**
     * Performs an action of a player.
     *
     * @param request the action, one of {@link #ACTIONS}
     * @param player  the session's player
     * @param args    the arguments
     * @return the result
     */
    ActionResult perform(ActionRequest request, UUID player, JsonNode args) {
        var online = onMain(() -> sources.online().apply(player)).join();
        if (online.isEmpty()) return answers.refused(request, NOT_ONLINE);
        var id = args.path("order").asInt(0);
        return switch (request.name()) {
            case Actions.ORDERS_DELIVER_COLLECT -> collect(request, online.get());
            case Actions.ORDERS_DELIVER_RECEIVE -> receive(request, online.get(), id);
            case Actions.ORDERS_DELIVER_ALL -> deliver(request, online.get(), id, null, 0);
            case Actions.ORDERS_DELIVER_LINE -> deliverLine(request, online.get(), id, args);
            default -> answers.refused(request, "dashboard.unsupported");
        };
    }

    private ActionResult deliverLine(ActionRequest request, Player player, int id, JsonNode args) {
        var line = args.path("line");
        var amount = args.path("amount");
        if (!line.isIntegralNumber() || !line.canConvertToInt() || line.asInt() < 1) {
            return answers.refused(request, "error.unknownLine");
        }
        if (!amount.isMissingNode() && !amount.isNull() && (!amount.canConvertToInt() || amount.asInt() < 1)) {
            return answers.refused(request, "dashboard.invalid");
        }
        var most = amount.isMissingNode() || amount.isNull() ? Integer.MAX_VALUE : amount.asInt();
        return deliver(request, player, id, line.asInt() - 1, most);
    }

    private ActionResult deliver(ActionRequest request, Player player, int id, Integer line, int amount) {
        if (id <= 0) return answers.refused(request, "error.unknownOrder");
        Optional<CompanyProfile> company =
                sources.companyOf().apply(player.getUniqueId()).join();
        if (company.isEmpty()) return answers.refused(request, "error.noMember");
        var found = sources.orders()
                .retrieveCompanyOrderById(id, company.get().id())
                .join();
        if (found == null || found.isEmpty()) return answers.refused(request, "error.unknownOrder");
        if (found.get().state() != OrderState.CLAIMED) return answers.refused(request, "error.orderNotActive");
        FullOrder order = sources.orders().retrieveFullOrder(found.get()).join();
        if (line != null && order.line(line).isEmpty()) return answers.refused(request, "error.unknownLine");
        var result = onMain(() -> {
                    if (!player.isOnline()) return CompletableFuture.<DeliveryResult>completedFuture(null);
                    return line == null
                            ? sources.delivery().all(player, company.get(), order)
                            : sources.delivery().line(player, company.get(), order, line, amount);
                })
                .thenCompose(Function.identity())
                .join();
        if (result == null) return answers.refused(request, NOT_ONLINE);
        return answers.data(request, Map.of("order", id, "delivered", result.delivered(), "done", result.orderDone()));
    }

    private ActionResult receive(ActionRequest request, Player player, int id) {
        if (id <= 0) return answers.refused(request, "error.unknownOrder");
        return switch (sources.receive().receive(player, id).join()) {
            case de.eldoria.companies.services.ActionResult.Refused<OrderReceiveService.Received> refused ->
                answers.refused(request, refused.failure());
            case de.eldoria.companies.services.ActionResult.Done<OrderReceiveService.Received> done ->
                answers.data(
                        request,
                        Map.of(
                                "order",
                                id,
                                "amount",
                                done.value().amount(),
                                "waiting",
                                done.value().waiting()));
        };
    }

    private ActionResult collect(ActionRequest request, Player player) {
        var collected = onMain(() -> sources.returns().collect(player, ReturnKind.OWNER))
                .thenCompose(Function.identity())
                .join();
        return answers.data(request, Map.of("received", collected.received(), "remaining", collected.remaining()));
    }

    private <T> CompletableFuture<T> onMain(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, sources.mainThread());
    }

    /**
     * Delivers to an order a company works on, on the main thread, as {@link DeliveryService} does.
     */
    public interface Deliveries {
        /**
         * @param worker  delivering player
         * @param company the worker's company
         * @param order   the order
         * @param line    line of the order, from zero
         * @param amount  the most items to deliver
         * @return what was delivered
         */
        CompletableFuture<DeliveryResult> line(
                Player worker, CompanyProfile company, FullOrder order, int line, int amount);

        /**
         * @param worker  delivering player
         * @param company the worker's company
         * @param order   the order
         * @return what was delivered
         */
        CompletableFuture<DeliveryResult> all(Player worker, CompanyProfile company, FullOrder order);

        /**
         * @param service the delivery service
         * @return its deliveries
         */
        static Deliveries of(DeliveryService service) {
            return new Deliveries() {
                @Override
                public CompletableFuture<DeliveryResult> line(
                        Player worker, CompanyProfile company, FullOrder order, int line, int amount) {
                    return service.deliver(worker, company, order, line, amount);
                }

                @Override
                public CompletableFuture<DeliveryResult> all(Player worker, CompanyProfile company, FullOrder order) {
                    return service.deliverAll(worker, company, order);
                }
            };
        }
    }

    /**
     * What the items are moved with.
     *
     * @param online     a player online on this node by id; called on the main thread
     * @param mainThread runs tasks on the main thread
     * @param companyOf  the company a player is a member of
     * @param orders     the order repository
     * @param delivery   delivers items as {@code /company order deliver} does, see {@link Deliveries#of}
     * @param receive    receives delivered orders as {@code /order receive} does
     * @param returns    hands out waiting items as {@code /order collect} does
     * @param render     renders a refusal as plain text
     */
    public record Sources(
            Function<UUID, Optional<Player>> online,
            Executor mainThread,
            Function<UUID, CompletableFuture<Optional<CompanyProfile>>> companyOf,
            AOrderData orders,
            Deliveries delivery,
            OrderReceiveService receive,
            ItemReturnService returns,
            Function<CommandFailure, String> render) {}
}
