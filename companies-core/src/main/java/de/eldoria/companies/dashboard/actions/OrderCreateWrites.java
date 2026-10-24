/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.order.IOrderDraft;
import de.eldoria.companies.components.order.OrderSource;
import de.eldoria.companies.dashboard.views.OrderCatalogue;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.companies.orders.OrderCreationService;
import de.eldoria.companies.orders.OrderDraftService;
import de.eldoria.companies.orders.Repetition;
import de.eldoria.companies.orders.definition.ItemDefinition;
import de.eldoria.companies.orders.definition.ItemDefinitions;
import de.eldoria.companies.services.SeriesService;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * Places an order from the dashboard, or with {@code "quote": true} names what it would cost.
 * <p>
 * The arguments are {@code name}, {@code lines} and an optional {@code repetition}. A line names its item by
 * {@code item}, a material key or a key of {@link OrderCatalogue} ({@code saved:<id>}, {@code earlier:<entry>:<line>}),
 * or defines it with {@code define} (see {@link ItemDefinition}), and gives {@code amount} and {@code unitPrice}. A
 * repetition has {@code interval} and either {@code times} or {@code until} ({@code yyyy-MM-dd}).
 * <p>
 * The order is built and checked as in game: the item rules decide whether a line is exact, the limits of lines and
 * items apply, the owner is charged. The player needs the permission to create orders, and to repeat them for a
 * repetition; the order limit comes from the permissions remembered with the session, else from the user settings.
 * The methods block on the database and the economy and are called off the main thread.
 */
public final class OrderCreateWrites {
    private static final String INVALID = "dashboard.invalid";

    private final Sources sources;
    private final Answers answers;

    /**
     * @param sources what orders are placed with
     */
    public OrderCreateWrites(Sources sources) {
        this.sources = sources;
        answers = new Answers(sources.render());
    }

    /**
     * Places or quotes an order of a session's player.
     *
     * @param request the action
     * @param session the session
     * @param args    the arguments
     * @return the result
     */
    ActionResult perform(ActionRequest request, StoredSession session, JsonNode args) {
        var permissions = session.permissions();
        if (!permissions.contains(Permission.Orders.CREATE)) {
            return answers.refused(request, "error.permission.createOrder");
        }
        var player = session.player();
        var name = args.path("name").asString("").strip();
        if (name.isEmpty()
                || !args.path("lines").isArray()
                || args.path("lines").isEmpty()) {
            return answers.refused(request, INVALID);
        }
        Optional<Repetition> repetition;
        List<Line> lines;
        try {
            repetition = repetition(args.path("repetition"));
            lines = lines(player, args.path("lines"));
        } catch (CommandFailure failure) {
            return answers.refused(request, failure);
        }
        var owner = sources.players().apply(player);
        var draft = draft(name, lines);
        if (args.path("quote").asBoolean(false)) return quote(request, owner, draft);
        var limit = limit(permissions);
        try {
            if (repetition.isPresent()) {
                var created = sources.series()
                        .start(
                                player,
                                builder(player, name, lines),
                                repetition.get(),
                                permissions.contains(Permission.Orders.REPEAT),
                                limit)
                        .join();
                return created(request, created.order().id(), created.price(), created.fee());
            }
            return switch (sources.creation()
                    .create(owner, draft, OrderSource.PLAYER, limit)
                    .join()) {
                case OrderCreationService.Created created ->
                    created(request, created.order().id(), created.price(), created.fee());
                case OrderCreationService.Rejected rejected -> answers.refused(request, rejected.failure());
            };
        } catch (CompletionException e) {
            if (e.getCause() instanceof CommandFailure failure) return answers.refused(request, failure);
            throw e;
        }
    }

    private ActionResult quote(ActionRequest request, OfflinePlayer owner, IOrderDraft draft) {
        return switch (sources.creation().quote(owner, draft)) {
            case OrderCreationService.Priced priced ->
                answers.data(request, Map.of("price", priced.price(), "fee", priced.fee(), "total", priced.total()));
            case OrderCreationService.Rejected rejected -> answers.refused(request, rejected.failure());
        };
    }

    private ActionResult created(ActionRequest request, int order, double price, double fee) {
        Map<String, Object> value = new HashMap<>();
        value.put("order", order);
        value.put("price", price);
        value.put("fee", fee);
        value.put("total", price + fee);
        return answers.data(request, value);
    }

    private int limit(Set<String> permissions) {
        var prefix = Permission.Orders.LIMIT + ".";
        return permissions.stream()
                .filter(node -> node.startsWith(prefix))
                .map(node -> node.substring(prefix.length()))
                .flatMap(number -> parse(number).stream())
                .findFirst()
                .orElseGet(() -> sources.defaultLimit().getAsInt());
    }

    private static Optional<Integer> parse(String number) {
        try {
            return Optional.of(Integer.parseInt(number));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private List<Line> lines(UUID player, JsonNode lines) {
        List<Line> result = new ArrayList<>();
        for (var line : lines) {
            var amount = line.path("amount");
            var price = line.path("unitPrice");
            if (!amount.isIntegralNumber() || amount.asInt() < 1 || !price.isNumber() || price.asDouble() < 0) {
                throw CommandFailure.of(INVALID);
            }
            var stack = item(player, line);
            var requested = sources.requests()
                    .apply(stack)
                    .orElseThrow(() -> CommandFailure.of("order.draft.error.notOrderable"));
            result.add(new Line(requested, amount.asInt(), price.asDouble()));
        }
        return result;
    }

    private ItemStack item(UUID player, JsonNode line) {
        if (line.path("define").isObject()) {
            return ItemDefinitions.build(definition(line.path("define")));
        }
        var key = line.path("item").asString("");
        if (key.startsWith("saved:") || key.startsWith("earlier:")) {
            return sources.catalogue()
                    .find(player, key)
                    .join()
                    .orElseThrow(() -> CommandFailure.of("dashboard.item.unknownItem"));
        }
        return new ItemStack(ItemDefinitions.material(key));
    }

    private static ItemDefinition definition(JsonNode define) {
        Map<String, Integer> enchantments = new HashMap<>();
        for (var entry : define.path("enchantments").properties()) {
            if (!entry.getValue().isIntegralNumber()) throw CommandFailure.of(INVALID);
            enchantments.put(entry.getKey(), entry.getValue().asInt());
        }
        var trim = define.path("trim");
        return new ItemDefinition(
                define.path("material").asString(""),
                enchantments,
                text(define.path("potion")),
                text(define.path("dye")),
                trim.isObject()
                        ? new ItemDefinition.Trim(
                                trim.path("pattern").asString(""),
                                trim.path("material").asString(""))
                        : null);
    }

    private static String text(JsonNode node) {
        return node.isString() && !node.asString().isBlank() ? node.asString() : null;
    }

    private static Optional<Repetition> repetition(JsonNode repetition) {
        if (repetition.isMissingNode() || repetition.isNull()) return Optional.empty();
        var interval = Repetition.interval(repetition.path("interval").asString(null))
                .orElseThrow(() -> CommandFailure.of(INVALID));
        var times = repetition.path("times");
        var until = repetition.path("until");
        try {
            return Optional.of(new Repetition(
                    interval,
                    times.isIntegralNumber() ? OptionalInt.of(times.asInt()) : OptionalInt.empty(),
                    until.isString() ? Optional.of(LocalDate.parse(until.asString())) : Optional.empty()));
        } catch (DateTimeParseException e) {
            throw CommandFailure.of(INVALID);
        }
    }

    private static IOrderDraft draft(String name, List<Line> lines) {
        var draft = IOrderDraft.of(name);
        for (var line : lines) {
            if (line.requested().exact()) {
                draft.addLine(line.requested().template(), line.amount(), line.unitPrice());
            } else {
                draft.addLine(line.requested().template().getType(), line.amount(), line.unitPrice());
            }
        }
        return draft;
    }

    private static OrderBuilder builder(UUID player, String name, List<Line> lines) {
        var builder = new OrderBuilder(player, name);
        for (var line : lines) {
            builder.add(line.requested().template(), line.requested().exact(), line.amount(), line.unitPrice());
        }
        return builder;
    }

    /**
     * A line of the order as the item rules requested it.
     *
     * @param requested the requested item
     * @param amount    the amount
     * @param unitPrice the price of one item
     */
    private record Line(OrderDraftService.RequestedItem requested, int amount, double unitPrice) {}

    /**
     * What orders are placed with.
     *
     * @param players      a player by id, online or not
     * @param creation     creates and prices orders
     * @param series       starts repeating orders
     * @param catalogue    finds saved and earlier items
     * @param requests     turns an item into what an order line requests, empty when it cannot be ordered
     * @param defaultLimit the order limit of players without a limit permission
     * @param render       renders a refusal as plain text
     */
    public record Sources(
            Function<UUID, OfflinePlayer> players,
            OrderCreationService creation,
            SeriesService series,
            OrderCatalogue catalogue,
            Function<ItemStack, Optional<OrderDraftService.RequestedItem>> requests,
            IntSupplier defaultLimit,
            Function<CommandFailure, String> render) {}
}
