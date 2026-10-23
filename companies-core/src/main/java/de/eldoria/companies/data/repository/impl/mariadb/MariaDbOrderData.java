/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.configuration.ConnectedQueryConfiguration;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.eldoria.companies.commands.company.order.search.SearchQuery;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.order.DeliveredStack;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.ItemAmount;
import de.eldoria.companies.data.wrapper.order.ItemReturn;
import de.eldoria.companies.data.wrapper.order.MaterialPrice;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.ReleasedOrders;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.orders.DeliveryPart;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

public class MariaDbOrderData extends AOrderData {

    /**
     * Create a new QueryBuilderFactory
     *
     * @param mapper
     */
    public MariaDbOrderData(ObjectMapper mapper) {
        super(mapper);
    }

    @Override
    protected Optional<Integer> putOrder(OfflinePlayer player, FullOrder order) {
        @Language("mariadb")
        var query = """
                INSERT
                INTO
                	orders(owner_uuid, name, fee)
                VALUES
                	(?, ?, ?)
                RETURNING id""";
        var conf = QueryConfiguration.getDefault().withSingleTransaction();
        Optional<Integer> optOrderId;
        try (conf) {
            optOrderId = conf.query(query)
                    .single(call().bind(player.getUniqueId(), UUID_BYTES)
                            .bind(order.name())
                            .bind(order.fee()))
                    .map(rs -> rs.getInt(1))
                    .first();
            if (optOrderId.isEmpty()) return Optional.empty();
            var orderId = optOrderId.get();

            query = """
                    INSERT INTO order_content(id, line, material, stack, exact, amount, price)
                    VALUES (?, ?, ?, ?, ?, ?, ?)""";
            var contents = order.contents();
            Stream<Call> calls = IntStream.range(0, contents.size()).mapToObj(line -> {
                var content = contents.get(line);
                return call().bind(orderId)
                        .bind(line)
                        .bind(content.stack().getType().name())
                        .bind(toJson(content.stack()))
                        .bind(content.exact())
                        .bind(content.amount())
                        .bind(content.price());
            });
            conf.query(query).batch(calls).insert();

            query = """
                    INSERT INTO order_states(id, state) VALUES(?, ?)""";
            conf.query(query)
                    .single(call().bind(orderId).bind(OrderState.UNCLAIMED.stateId()))
                    .update();
        }
        if (!conf.context().exceptions().isEmpty()) return Optional.empty();
        return optOrderId;
    }

    @Override
    protected boolean updateOrderState(SimpleOrder order, OrderState expected, OrderState state) {
        @Language("mariadb")
        var query = """
                UPDATE order_states
                SET state = ?
                WHERE id = ?
                  AND state = ?""";
        return query(query)
                .single(call().bind(state.stateId()).bind(order.id()).bind(expected.stateId()))
                .update()
                .changed();
    }

    @Override
    protected List<SimpleOrder> getExpiredOrders(int hours) {
        @Language("mariadb")
        var query = """
                SELECT
                    o.id,
                    last_update,
                    company,
                    state,
                    owner_uuid,
                    name,
                    created,
                    o.fee,
                    extensions,
                    delivery_bonus,
                    series_id
                FROM
                    order_states s
                        LEFT JOIN orders o
                        ON o.id = s.id
                WHERE last_update < NOW() - INTERVAL (? + delivery_bonus) HOUR
                  AND company IS NOT NULL
                  AND state = ?
                ORDER BY last_update""";
        return query(query)
                .single(call().bind(hours).bind(OrderState.CLAIMED.stateId()))
                .map(this::buildSimpleOrder)
                .all();
    }

    @Override
    protected List<SimpleOrder> getDeadOrders(int hours) {
        @Language("mariadb")
        var query = """
                SELECT
                    o.id,
                    last_update,
                    company,
                    state,
                    owner_uuid,
                    name,
                    created,
                    o.fee,
                    extensions,
                    delivery_bonus,
                    series_id
                FROM
                    order_states s
                        JOIN orders o
                        ON o.id = s.id
                WHERE last_update < NOW() - INTERVAL ? HOUR
                  AND state = ?
                ORDER BY last_update""";
        return query(query)
                .single(call().bind(hours).bind(OrderState.UNCLAIMED.stateId()))
                .map(this::buildSimpleOrder)
                .all();
    }

    @Override
    protected boolean claimOrder(SimpleCompany company, SimpleOrder order, int deliveryBonus) {
        @Language("mariadb")
        var query = """
                UPDATE order_states
                SET state          = ?,
                    company        = ?,
                    delivery_bonus = ?,
                    last_update    = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND state = ?""";
        return query(query)
                .single(call().bind(OrderState.CLAIMED.stateId())
                        .bind(company.id())
                        .bind(deliveryBonus)
                        .bind(order.id())
                        .bind(OrderState.UNCLAIMED.stateId()))
                .update()
                .changed();
    }

    @Override
    protected boolean orderDelivered(SimpleOrder order) {
        @Language("mariadb")
        var update = """
                UPDATE order_states
                SET state       = ?,
                    last_update = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND state = ?
                  AND NOT EXISTS(SELECT 1
                                 FROM order_content c
                                 WHERE c.id = order_states.id
                                   AND c.amount > COALESCE((SELECT SUM(d.amount)
                                                            FROM order_deliveries d
                                                            WHERE d.order_id = c.id
                                                              AND d.line = c.line), 0))""";
        return query(update)
                .single(call().bind(OrderState.DELIVERED.stateId())
                        .bind(order.id())
                        .bind(OrderState.CLAIMED.stateId()))
                .update()
                .changed();
    }

    @Override
    protected Optional<Set<UUID>> unclaimOrder(SimpleOrder order) {
        @Language("mariadb")
        var query = """
                UPDATE order_states
                SET state          = ?,
                    company        = NULL,
                    delivery_bonus = 0,
                    last_update    = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND state = ?""";
        var conf = QueryConfiguration.getDefault().withSingleTransaction();
        List<UUID> workers;
        try (conf) {
            var unclaimed = conf.query(query)
                    .single(call().bind(OrderState.UNCLAIMED.stateId())
                            .bind(order.id())
                            .bind(OrderState.CLAIMED.stateId()))
                    .update();
            if (!unclaimed.changed()) return Optional.empty();
            workers = returnDeliveries(conf, order);
        }
        if (!conf.context().exceptions().isEmpty()) return Optional.empty();
        return Optional.of(Set.copyOf(workers));
    }

    /**
     * Moves the stacks delivered to an order unchanged to the item returns of their workers and removes them from the
     * order.
     *
     * @param conf  open transaction
     * @param order order whose deliveries are returned
     * @return the workers who got items returned
     */
    private List<UUID> returnDeliveries(ConnectedQueryConfiguration conf, SimpleOrder order) {
        @Language("mariadb")
        var workerQuery = """
                SELECT DISTINCT worker_uuid
                FROM order_deliveries
                WHERE order_id = ?
                  AND amount > 0""";
        @Language("mariadb")
        var returnItems = """
                INSERT INTO item_returns(worker_uuid, material, stack, amount)
                SELECT d.worker_uuid,
                       c.material,
                       d.stack,
                       d.amount
                FROM order_deliveries d
                         JOIN order_content c
                              ON c.id = d.order_id
                                  AND c.line = d.line
                WHERE d.order_id = ?
                  AND d.amount > 0
                ORDER BY d.id""";
        @Language("mariadb")
        var delete = """
                DELETE FROM order_deliveries WHERE order_id = ?""";
        var workers = conf.query(workerQuery)
                .single(call().bind(order.id()))
                .map(row -> row.get("worker_uuid", UUID_BYTES))
                .all();
        conf.query(returnItems).single(call().bind(order.id())).insert();
        conf.query(delete).single(call().bind(order.id())).delete();
        return workers;
    }

    @Override
    protected boolean forceDelivered(SimpleOrder order) {
        @Language("mariadb")
        var query = """
                UPDATE order_states
                SET state       = ?,
                    last_update = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND state = ?""";
        return query(query)
                .single(call().bind(OrderState.DELIVERED.stateId())
                        .bind(order.id())
                        .bind(OrderState.CLAIMED.stateId()))
                .update()
                .changed();
    }

    @Override
    protected Optional<Set<UUID>> adminDelete(SimpleOrder order) {
        var conf = QueryConfiguration.getDefault().withSingleTransaction();
        Optional<List<UUID>> workers;
        try (conf) {
            workers = deleteLocked(conf, order);
        }
        if (workers.isEmpty() || !conf.context().exceptions().isEmpty()) return Optional.empty();
        return Optional.of(Set.copyOf(workers.get()));
    }

    /**
     * Deletes an unclaimed or claimed order inside a transaction, returning the deliveries of a claimed one first.
     *
     * @param conf  open transaction
     * @param order order to delete
     * @return the workers who got items returned, or empty when the order was neither unclaimed nor claimed
     */
    private Optional<List<UUID>> deleteLocked(ConnectedQueryConfiguration conf, SimpleOrder order) {
        List<UUID> workers = List.of();
        if (lockOrder(conf, order, OrderState.CLAIMED)) {
            workers = returnDeliveries(conf, order);
        } else if (!lockOrder(conf, order, OrderState.UNCLAIMED)) {
            return Optional.empty();
        }
        for (var table : List.of("order_content", "order_states", "orders")) {
            conf.query("DELETE FROM %s WHERE id = ?", table)
                    .single(call().bind(order.id()))
                    .delete();
        }
        return Optional.of(workers);
    }

    @Override
    protected boolean extendClaim(SimpleOrder order, int hours) {
        @Language("mariadb")
        var query = """
                UPDATE order_states
                SET delivery_bonus = delivery_bonus + ?
                WHERE id = ?
                  AND state = ?""";
        return query(query)
                .single(call().bind(hours).bind(order.id()).bind(OrderState.CLAIMED.stateId()))
                .update()
                .changed();
    }

    @Override
    protected boolean extendUnclaimed(SimpleOrder order, int hours) {
        @Language("mariadb")
        var query = """
                UPDATE order_states
                SET last_update = last_update + INTERVAL ? HOUR
                WHERE id = ?
                  AND state = ?""";
        return moveLastUpdate(query, order, hours);
    }

    /**
     * Runs the dialect's statement moving the last update of an unclaimed order.
     *
     * @param query update with the parameters hours, order id and state
     * @param order order to change
     * @param hours hours to add
     * @return true if the order was unclaimed and changed
     */
    protected boolean moveLastUpdate(String query, SimpleOrder order, int hours) {
        return query(query)
                .single(call().bind(hours).bind(order.id()).bind(OrderState.UNCLAIMED.stateId()))
                .update()
                .changed();
    }

    @Override
    protected List<SimpleOrder> ordersForAdmin(@Nullable UUID owner, @Nullable Integer company, int page, int size) {
        @Language("mariadb")
        var query = """
                SELECT
                    o.id,
                    owner_uuid,
                    name,
                    created,
                    company,
                    last_update,
                    state,
                    o.fee,
                    extensions,
                    delivery_bonus,
                    series_id
                FROM
                    orders o
                        LEFT JOIN order_states oc
                        ON o.id = oc.id
                WHERE TRUE
                  %s
                  %s
                ORDER BY o.id DESC
                LIMIT ? OFFSET ?""";
        var call = call();
        if (owner != null) call = call.bind(owner, UUID_BYTES);
        if (company != null) call = call.bind(company);
        return query(query, owner == null ? "" : "AND o.owner_uuid = ?", company == null ? "" : "AND oc.company = ?")
                .single(call.bind(size).bind((page - 1) * size))
                .map(this::buildSimpleOrder)
                .all();
    }

    @Override
    protected boolean extendOrder(SimpleOrder order, int maxExtensions) {
        @Language("mariadb")
        var query = """
                UPDATE order_states
                SET last_update = CURRENT_TIMESTAMP,
                    extensions  = extensions + 1
                WHERE id = ?
                  AND state = ?
                  AND extensions < ?""";
        return query(query)
                .single(call().bind(order.id())
                        .bind(OrderState.UNCLAIMED.stateId())
                        .bind(maxExtensions))
                .update()
                .changed();
    }

    @Override
    protected List<SimpleOrder> getPurgeWarnings(int hours) {
        @Language("mariadb")
        var query = """
                SELECT
                    o.id,
                    last_update,
                    company,
                    state,
                    owner_uuid,
                    name,
                    created,
                    o.fee,
                    extensions,
                    delivery_bonus,
                    series_id
                FROM
                    order_states s
                        JOIN orders o
                        ON o.id = s.id
                WHERE last_update < NOW() - INTERVAL ? HOUR
                  AND state = ?
                  AND (purge_warning IS NULL OR purge_warning <= last_update)
                ORDER BY last_update""";
        return query(query)
                .single(call().bind(hours).bind(OrderState.UNCLAIMED.stateId()))
                .map(this::buildSimpleOrder)
                .all();
    }

    @Override
    protected boolean markPurgeWarning(SimpleOrder order) {
        @Language("mariadb")
        var query = """
                UPDATE order_states
                SET purge_warning = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND state = ?
                  AND (purge_warning IS NULL OR purge_warning <= last_update)""";
        return query(query)
                .single(call().bind(order.id()).bind(OrderState.UNCLAIMED.stateId()))
                .update()
                .changed();
    }

    @Override
    protected boolean raisePrice(SimpleOrder order, Map<Integer, Double> increases, double extraFee) {
        @Language("mariadb")
        var raise = """
                UPDATE order_content
                SET price = price + ?
                WHERE id = ?
                  AND line = ?""";
        @Language("mariadb")
        var fee = """
                UPDATE orders
                SET fee = fee + ?
                WHERE id = ?""";
        var conf = QueryConfiguration.getDefault().withSingleTransaction();
        try (conf) {
            if (!lockOrder(conf, order, OrderState.UNCLAIMED)) return false;
            for (var increase : increases.entrySet()) {
                conf.query(raise)
                        .single(call().bind(increase.getValue())
                                .bind(order.id())
                                .bind(increase.getKey()))
                        .update();
            }
            conf.query(fee).single(call().bind(extraFee).bind(order.id())).update();
        }
        return conf.context().exceptions().isEmpty();
    }

    @Override
    protected boolean receiveOrder(SimpleOrder order, List<ItemAmount> pending) {
        @Language("mariadb")
        var receive = """
                UPDATE order_states
                SET state = ?
                WHERE id = ?
                  AND state = ?""";
        var conf = QueryConfiguration.getDefault().withSingleTransaction();
        try (conf) {
            var received = conf.query(receive)
                    .single(call().bind(OrderState.RECEIVED.stateId())
                            .bind(order.id())
                            .bind(OrderState.DELIVERED.stateId()))
                    .update();
            if (!received.changed()) return false;
            for (var item : pending) {
                conf.query(itemReturnInsert())
                        .single(itemReturnCall(order.owner(), ReturnKind.OWNER, item.stack(), item.amount()))
                        .insert();
            }
        }
        return conf.context().exceptions().isEmpty();
    }

    @Override
    protected List<ItemReturn> getItemReturns(UUID player, ReturnKind kind) {
        @Language("mariadb")
        var query = """
                SELECT id, stack, amount
                FROM item_returns
                WHERE worker_uuid = ?
                  AND kind = ?
                ORDER BY id""";
        return query(query)
                .single(call().bind(player, UUID_BYTES).bind(kind.id()))
                .map(row -> new ItemReturn(row.getInt("id"), toItemStack(row.getString("stack")), row.getInt("amount")))
                .all();
    }

    @Override
    protected boolean takeItemReturn(ItemReturn itemReturn, int amount) {
        @Language("mariadb")
        var take = """
                UPDATE item_returns
                SET amount = amount - ?
                WHERE id = ?
                  AND amount >= ?""";
        @Language("mariadb")
        var delete = """
                DELETE FROM item_returns WHERE id = ? AND amount <= 0""";
        var taken = query(take)
                .single(call().bind(amount).bind(itemReturn.id()).bind(amount))
                .update()
                .changed();
        if (taken) {
            query(delete).single(call().bind(itemReturn.id())).delete();
        }
        return taken;
    }

    @Override
    protected int countItemReturns(UUID player, ReturnKind kind) {
        @Language("mariadb")
        var query = """
                SELECT COALESCE(SUM(amount), 0) AS amount
                FROM item_returns
                WHERE worker_uuid = ?
                  AND kind = ?""";
        return query(query)
                .single(call().bind(player, UUID_BYTES).bind(kind.id()))
                .map(row -> row.getInt("amount"))
                .first()
                .orElse(0);
    }

    @Override
    protected boolean addItemReturn(UUID player, ReturnKind kind, ItemStack stack, int amount) {
        return query(itemReturnInsert())
                .single(itemReturnCall(player, kind, stack, amount))
                .insert()
                .changed();
    }

    private static String itemReturnInsert() {
        return """
                INSERT INTO item_returns(worker_uuid, kind, material, stack, amount)
                VALUES (?, ?, ?, ?, ?)""";
    }

    private Call itemReturnCall(UUID player, ReturnKind kind, ItemStack stack, int amount) {
        return call().bind(player, UUID_BYTES)
                .bind(kind.id())
                .bind(stack.getType().name())
                .bind(toJson(stack))
                .bind(amount);
    }

    @Override
    protected List<Integer> deliver(OfflinePlayer player, SimpleOrder order, List<DeliveryPart> parts) {
        @Language("mariadb")
        var missingQuery = """
                SELECT c.line,
                       c.amount - COALESCE((SELECT SUM(d.amount)
                                            FROM order_deliveries d
                                            WHERE d.order_id = c.id
                                              AND d.line = c.line), 0) AS missing
                FROM order_content c
                WHERE c.id = ?""";
        @Language("mariadb")
        var insert = """
                INSERT INTO order_deliveries(order_id, line, worker_uuid, stack, amount)
                VALUES (?, ?, ?, ?, ?)""";
        List<Integer> recorded = new ArrayList<>();
        var conf = QueryConfiguration.getDefault().withSingleTransaction();
        try (conf) {
            if (!lockOrder(conf, order, OrderState.CLAIMED)) return nothingRecorded(parts);
            Map<Integer, Integer> missing = new HashMap<>();
            conf.query(missingQuery)
                    .single(call().bind(order.id()))
                    .map(row -> Map.entry(row.getInt("line"), row.getInt("missing")))
                    .all()
                    .forEach(entry -> missing.put(entry.getKey(), entry.getValue()));
            for (var part : parts) {
                var left = missing.getOrDefault(part.line(), 0);
                var amount = Math.max(0, Math.min(part.amount(), left));
                recorded.add(amount);
                if (amount == 0) continue;
                missing.put(part.line(), left - amount);
                conf.query(insert)
                        .single(call().bind(order.id())
                                .bind(part.line())
                                .bind(player.getUniqueId(), UUID_BYTES)
                                .bind(toJson(single(part.stack())))
                                .bind(amount))
                        .insert();
            }
        }
        return conf.context().exceptions().isEmpty() ? recorded : nothingRecorded(parts);
    }

    private static List<Integer> nothingRecorded(List<DeliveryPart> parts) {
        return parts.stream().map(part -> 0).toList();
    }

    private static ItemStack single(ItemStack stack) {
        var single = stack.clone();
        single.setAmount(1);
        return single;
    }

    /**
     * Locks the state of the order for the rest of the transaction if the order is in the given state.
     * <p>
     * Concurrent changes to the same order wait for each other, so a delivery can not exceed the missing amount and a
     * price raise can not overlap with an accept.
     *
     * @param conf  transaction to lock in
     * @param order order to lock
     * @param state state the order has to be in
     * @return true if the order is in the state
     */
    protected boolean lockOrder(ConnectedQueryConfiguration conf, SimpleOrder order, OrderState state) {
        @Language("mariadb")
        var query = """
                SELECT id
                FROM order_states
                WHERE id = ?
                  AND state = ?
                    FOR UPDATE""";
        return conf.query(query)
                .single(call().bind(order.id()).bind(state.stateId()))
                .map(row -> row.getInt("id"))
                .first()
                .isPresent();
    }

    @Override
    protected Optional<SimpleOrder> orderById(int id) {
        @Language("mariadb")
        var query = """
                SELECT
                    o.id,
                    owner_uuid,
                    name,
                    created,
                    company,
                    last_update,
                    state,
                    o.fee,
                    extensions,
                    delivery_bonus,
                    series_id
                FROM
                    orders o
                        LEFT JOIN order_states oc
                        ON o.id = oc.id
                WHERE o.id = ?""";
        return query(query).single(call().bind(id)).map(this::buildSimpleOrder).first();
    }

    @Override
    protected Optional<SimpleOrder> companyOrderById(int id, int company) {
        @Language("mariadb")
        var query = """
                SELECT
                    o.id,
                    owner_uuid,
                    name,
                    created,
                    company,
                    last_update,
                    state,
                    o.fee,
                    extensions,
                    delivery_bonus,
                    series_id
                FROM
                    orders o
                        LEFT JOIN order_states oc
                        ON o.id = oc.id
                WHERE o.id = ?
                  AND company = ?""";
        return query(query)
                .single(call().bind(id).bind(company))
                .map(this::buildSimpleOrder)
                .first();
    }

    @Override
    protected List<SimpleOrder> ordersByCompany(ISimpleCompany company, OrderState min, OrderState max) {
        @Language("mariadb")
        var query = """
                SELECT
                    o.id,
                    owner_uuid,
                    name,
                    created,
                    company,
                    last_update,
                    state,
                    o.fee,
                    extensions,
                    delivery_bonus,
                    series_id
                FROM
                    orders o
                        LEFT JOIN order_states oc
                        ON o.id = oc.id
                WHERE oc.company = ?
                  AND state >= ?
                  AND state <= ?""";
        return query(query)
                .single(call().bind(company.id()).bind(min.stateId()).bind(max.stateId()))
                .map(this::buildSimpleOrder)
                .all();
    }

    @Override
    protected List<SimpleOrder> ordersByPlayer(OfflinePlayer player, OrderState min, OrderState max) {
        @Language("mariadb")
        var query = """
                SELECT
                    o.id,
                    owner_uuid,
                    name,
                    created,
                    company,
                    last_update,
                    state,
                    o.fee,
                    extensions,
                    delivery_bonus,
                    series_id
                FROM
                    orders o
                        LEFT JOIN order_states oc
                        ON o.id = oc.id
                WHERE o.owner_uuid = ?
                  AND state >= ?
                  AND state <= ?""";
        return query(query)
                .single(call().bind(player.getUniqueId(), UUID_BYTES)
                        .bind(min.stateId())
                        .bind(max.stateId()))
                .map(this::buildSimpleOrder)
                .all();
    }

    @Override
    protected Integer getPlayerOrderCount(OfflinePlayer player) {
        @Language("mariadb")
        var query = """
                SELECT
                    COUNT(1) AS count
                FROM
                    orders
                        LEFT JOIN order_states s
                        ON orders.id = s.id
                WHERE owner_uuid = ?
                  AND s.state < ?""";
        return query(query)
                .single(call().bind(player.getUniqueId(), UUID_BYTES).bind(OrderState.DELIVERED.stateId()))
                .map(rs -> rs.getInt("count"))
                .first()
                .get();
    }

    @Override
    protected Integer getCompanyOrderCount(ISimpleCompany company) {
        @Language("mariadb")
        var query = """
                SELECT
                    COUNT(1) AS count
                FROM
                    orders
                        LEFT JOIN order_states s
                        ON orders.id = s.id
                WHERE company = ?
                  AND s.state = ?""";
        return query(query)
                .single(call().bind(company.id()).bind(OrderState.CLAIMED.stateId()))
                .map(rs -> rs.getInt("count"))
                .first()
                .get();
    }

    @Override
    protected List<OrderContent> getOrderContent(SimpleOrder order) {
        @Language("mariadb")
        var query = """
                SELECT line, stack, exact, amount, price
                FROM order_content
                WHERE id = ?
                ORDER BY line""";
        var orderContents = query(query)
                .single(call().bind(order.id()))
                .map(row -> new OrderContent(
                        row.getInt("line"),
                        toItemStack(row.getString("stack")),
                        row.getBoolean("exact"),
                        row.getInt("amount"),
                        row.getDouble("price")))
                .all();

        for (var orderContent : orderContents) {
            orderContent.deliveries(getDeliveredStacks(order, orderContent.line()));
        }
        return orderContents;
    }

    @Override
    protected List<DeliveredStack> getDeliveredStacks(SimpleOrder order, int line) {
        @Language("mariadb")
        var query = """
                SELECT id, worker_uuid, stack, amount
                FROM order_deliveries
                WHERE order_id = ?
                  AND line = ?
                ORDER BY id""";
        return query(query)
                .single(call().bind(order.id()).bind(line))
                .map(row -> new DeliveredStack(
                        row.getInt("id"),
                        row.get("worker_uuid", UUID_BYTES),
                        toItemStack(row.getString("stack")),
                        row.getInt("amount")))
                .all();
    }

    @Override
    protected ReleasedOrders purgeCompanyOrders(SimpleCompany profile) {
        Set<UUID> workers = new HashSet<>();
        List<SimpleOrder> released = new ArrayList<>();
        for (var simpleOrder : ordersByCompany(profile, OrderState.CLAIMED, OrderState.CLAIMED)) {
            var returned = unclaimOrder(simpleOrder);
            invalidateFullOrder(simpleOrder);
            if (returned.isEmpty()) continue;
            workers.addAll(returned.get());
            released.add(simpleOrder);
        }
        return new ReleasedOrders(released, workers);
    }

    @Override
    protected boolean purgeOrder(SimpleOrder order) {
        return deleteOrder(order);
    }

    @Override
    protected boolean deleteOrder(SimpleOrder order) {
        @Language("mariadb")
        var deleteState = """
                DELETE
                FROM order_states
                WHERE id = ?
                  AND state = ?""";
        @Language("mariadb")
        var deleteOrder = """
                DELETE
                FROM orders
                WHERE id = ?""";
        var conf = QueryConfiguration.getDefault().withSingleTransaction();
        try (conf) {
            var unclaimed = conf.query(deleteState)
                    .single(call().bind(order.id()).bind(OrderState.UNCLAIMED.stateId()))
                    .delete();
            if (!unclaimed.changed()) return false;
            conf.query(deleteOrder).single(call().bind(order.id())).delete();
        }
        return conf.context().exceptions().isEmpty();
    }

    @Override
    protected List<FullOrder> getOrdersByQuery(SearchQuery searchQuery, OrderState min, OrderState max) {
        @Language("mariadb")
        var query = """
                SELECT o.id, o.owner_uuid, o.name, o.created, os.company, os.last_update, os.state, o.fee, os.extensions, os.delivery_bonus, o.series_id
                FROM orders o
                         LEFT JOIN (SELECT c.id, GROUP_CONCAT(c.material, ' ') AS materials, SUM(amount) AS amount, SUM(price) AS price
                                    FROM order_content c
                                    GROUP BY id) oc
                                   ON o.id = oc.id
                         LEFT JOIN order_states os
                                   ON o.id = os.id
                WHERE o.name LIKE ?
                  AND oc.materials REGEXP ?
                  AND oc.price >= ?
                  AND oc.price <= ?
                  AND oc.amount >= ?
                  AND oc.amount <= ?
                  AND os.state >= ?
                  AND os.state <= ?;""";
        var orders = query(query)
                .single(call().bind("%" + searchQuery.name() + "%")
                        .bind(searchQuery.materialRegex())
                        .bind(searchQuery.minPrice())
                        .bind(searchQuery.maxPrice())
                        .bind(searchQuery.minOrderSize())
                        .bind(searchQuery.maxOrderSize())
                        .bind(min.stateId())
                        .bind(max.stateId()))
                .map(this::buildSimpleOrder)
                .all();
        var fullOrders = toFullOrders(orders);
        searchQuery.sort(fullOrders);
        return fullOrders;
    }

    @Override
    protected Optional<MaterialPrice> findMaterialPrice(String material) {
        @Language("mariadb")
        var query = """
                SELECT material, avg_price, min_price, max_price
                FROM material_price
                WHERE material = ?""";
        return query(query)
                .single(call().bind(material.toUpperCase(Locale.ROOT)))
                .map(rs -> new MaterialPrice(
                        material.toLowerCase(Locale.ROOT),
                        rs.getDouble("avg_price"),
                        rs.getDouble("min_price"),
                        rs.getDouble("max_price")))
                .first();
    }

    @Override
    public void refreshMaterialPrices() {
        @Language("mariadb")
        var query = """
                INSERT INTO material_price(material, avg_price, min_price, max_price)
                SELECT material, avg_price, min_price, max_price
                FROM (SELECT c.material,
                             AVG(c.price / c.amount) AS avg_price,
                             MIN(c.price / c.amount) AS min_price,
                             MAX(c.price / c.amount) AS max_price
                      FROM (SELECT ROW_NUMBER() OVER (PARTITION BY material ORDER BY last_update DESC) AS id,
                                   material,
                                   amount,
                                   price,
                                   last_update
                            FROM order_content c
                                     LEFT JOIN order_states s ON c.id = s.id
                            WHERE s.state >= 200
                              AND NOT c.exact) c
                      WHERE c.id < 100
                      GROUP BY c.material) avg
                WHERE TRUE
                ON DUPLICATE KEY UPDATE avg_price = avg.avg_price,
                                        min_price = avg.min_price,
                                        max_price = avg.max_price;""";
        query(query).single().update();
    }
}
