/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.order.IOrderDraft;
import de.eldoria.companies.components.order.ItemCriteria;
import de.eldoria.companies.components.order.OrderSource;
import de.eldoria.companies.dashboard.views.OrderCatalogue;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.companies.orders.OrderCreationService;
import de.eldoria.companies.orders.OrderDraftService;
import de.eldoria.companies.orders.Repetition;
import de.eldoria.companies.orders.criteria.CriteriaRules;
import de.eldoria.companies.orders.definition.ItemDefinition;
import de.eldoria.companies.orders.definition.ItemDefinitions;
import de.eldoria.companies.services.SeriesService;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
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
 * Places an order from the dashboard ({@link Actions#ORDERS_CREATE}), or names what it would cost without placing it
 * ({@link Actions#ORDERS_QUOTE}).
 * <p>
 * The arguments are {@code name}, {@code lines} and an optional {@code repetition}. A line names its item by
 * {@code item}, a material key or a key of {@link OrderCatalogue} ({@code saved:<id>}, {@code earlier:<entry>:<line>}),
 * or defines it with {@code define} (see {@link ItemDefinition}), and gives {@code amount} and {@code unitPrice}. A
 * repetition has {@code interval} and either {@code times} or {@code until} ({@code yyyy-MM-dd}).
 * <p>
 * The order is built and checked as in game: the item rules decide whether a line is exact, the limits of lines and
 * items apply, the owner is charged. A quote checks all of it except the limit of active orders and the balance, and a
 * repeating order is checked like a quote before its series is saved, so lines, items or a name its first copy would be
 * refused for leave no series behind; a first copy the balance or a listener refuses stops the series as in game.
 * Lines beyond the limit of lines are refused before any item is looked up. The player needs the permission to create orders, and to repeat them for a
 * repetition; the order limit comes from the permissions remembered with the session, else from the user settings.
 * The methods block on the database and the economy and are called off the main thread.
 */
public final class OrderCreateWrites {
    /**
     * The actions performed here.
     */
    public static final Set<String> ACTIONS = Set.of(Actions.ORDERS_CREATE, Actions.ORDERS_QUOTE);

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
     * @param request the action, one of {@link #ACTIONS}
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
        if (args.path("lines").size() > sources.creation().maxLines()) {
            return answers.refused(request, "order.create.error.materialLimit");
        }
        Optional<Repetition> repetition;
        List<Line> lines;
        try {
            repetition = repetition(args.path("repetition"));
            lines = lines(player, args.path("lines"));
            if (repetition.isPresent()) {
                sources.series().check(repetition.get(), permissions.contains(Permission.Orders.REPEAT));
            }
        } catch (CommandFailure failure) {
            return answers.refused(request, failure);
        }
        var owner = sources.players().apply(player);
        var draft = draft(name, lines);
        var quote = sources.creation().quote(owner, draft);
        if (quote instanceof OrderCreationService.Rejected rejected)
            return answers.refused(request, rejected.failure());
        if (request.name().equals(Actions.ORDERS_QUOTE)) return priced(request, (OrderCreationService.Priced) quote);
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
                    .create(owner, draft, OrderSource.DASHBOARD, limit)
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

    private ActionResult priced(ActionRequest request, OrderCreationService.Priced priced) {
        return answers.data(request, Map.of("price", priced.price(), "fee", priced.fee(), "total", priced.total()));
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
            if (!whole(amount)
                    || amount.asInt() < 1
                    || !price.isNumber()
                    || !Double.isFinite(price.asDouble())
                    || price.asDouble() < 0) {
                throw CommandFailure.of(INVALID);
            }
            if (line.path("criteria").isObject()) {
                var criteria = criteria(line.path("criteria"));
                var problem = CriteriaRules.problem(criteria);
                if (problem.isPresent()) throw problem.get();
                var requested = new OrderDraftService.RequestedItem(criteria.template(), false);
                result.add(new Line(requested, amount.asInt(), price.asDouble(), criteria));
                continue;
            }
            var stack = item(player, line);
            var requested = sources.requests()
                    .apply(stack)
                    .orElseThrow(() -> CommandFailure.of("order.draft.error.notOrderable"));
            result.add(new Line(requested, amount.asInt(), price.asDouble(), null));
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

    private static ItemCriteria criteria(JsonNode criteria) {
        List<String> items = new ArrayList<>();
        for (var item : criteria.path("items")) {
            if (!item.isString()) throw CommandFailure.of(INVALID);
            items.add(item.asString());
        }
        var count = criteria.path("minEnchantments");
        if (!count.isMissingNode() && !count.isNull() && !whole(count)) throw CommandFailure.of(INVALID);
        var required = criteria.path("enchantments");
        if (!required.isMissingNode() && !required.isNull() && !required.isObject()) throw CommandFailure.of(INVALID);
        var potion = criteria.path("potion");
        if (!potion.isMissingNode() && !potion.isNull() && !potion.isString()) throw CommandFailure.of(INVALID);
        var durability = criteria.path("minDurability");
        if (!durability.isMissingNode() && !durability.isNull() && !whole(durability)) throw CommandFailure.of(INVALID);
        try {
            List<ItemCriteria.Required> enchantments = new ArrayList<>();
            for (var entry : required.properties()) {
                if (!whole(entry.getValue())) throw CommandFailure.of(INVALID);
                enchantments.add(new ItemCriteria.Required(
                        entry.getKey(), entry.getValue().asInt()));
            }
            return new ItemCriteria(
                    items,
                    whole(count) ? count.asInt() : 0,
                    enchantments,
                    potion.isString() ? potion.asString() : null,
                    whole(durability) ? durability.asInt() : 0);
        } catch (IllegalArgumentException e) {
            throw CommandFailure.of(INVALID);
        }
    }

    private static ItemDefinition definition(JsonNode define) {
        Map<String, Integer> enchantments = new HashMap<>();
        for (var entry : define.path("enchantments").properties()) {
            if (!whole(entry.getValue())) throw CommandFailure.of(INVALID);
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

    private static boolean whole(JsonNode node) {
        return node.isIntegralNumber() && node.canConvertToInt();
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
        if (!times.isMissingNode() && !times.isNull() && !whole(times)) throw CommandFailure.of(INVALID);
        try {
            return Optional.of(new Repetition(
                    interval,
                    whole(times) ? OptionalInt.of(times.asInt()) : OptionalInt.empty(),
                    until.isString() ? Optional.of(LocalDate.parse(until.asString())) : Optional.empty()));
        } catch (DateTimeParseException e) {
            throw CommandFailure.of(INVALID);
        }
    }

    private static IOrderDraft draft(String name, List<Line> lines) {
        var draft = IOrderDraft.of(name);
        for (var line : lines) {
            var criteria = line.criteria();
            if (criteria != null) {
                draft.addLine(criteria, line.amount(), line.unitPrice());
            } else if (line.requested().exact()) {
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
            var criteria = line.criteria();
            if (criteria != null) {
                builder.add(criteria, line.amount(), line.unitPrice());
            } else {
                builder.add(line.requested().template(), line.requested().exact(), line.amount(), line.unitPrice());
            }
        }
        return builder;
    }

    /**
     * A line of the order as the item rules requested it.
     *
     * @param requested the requested item, for a criteria line the item standing for it
     * @param amount    the amount
     * @param unitPrice the price of one item
     * @param criteria  what a criteria line accepts, null for material and exact lines
     */
    private record Line(
            OrderDraftService.RequestedItem requested,
            int amount,
            double unitPrice,
            @Nullable ItemCriteria criteria) {}

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
