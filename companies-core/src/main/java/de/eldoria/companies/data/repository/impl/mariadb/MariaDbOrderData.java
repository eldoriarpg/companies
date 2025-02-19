/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.configuration.QueryConfiguration;
import de.eldoria.companies.commands.company.order.search.SearchQuery;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.order.ContentPart;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.MaterialPrice;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.intellij.lang.annotations.Language;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
    protected void putOrder(OfflinePlayer player, FullOrder order) {
        @Language("mariadb")
        var query = """
                INSERT
                INTO
                	orders(owner_uuid, name)
                VALUES
                	(?, ?)
                RETURNING id""";
        try (var conf = QueryConfiguration.getDefault().withSingleTransaction()) {
            var orderId = conf.query(query)
                    .single(call().bind(player.getUniqueId(), UUID_BYTES)
                            .bind(order.name()))
                    .map(rs -> rs.getInt(1))
                    .first()
                    .get();

            query = """
                    INSERT INTO order_content(id, material, stack, amount, price)
                    VALUES (?, ?, ?, ?, ?)""";
            Stream<Call> calls = order.contents().stream()
                    .map(content -> call().bind(orderId)
                            .bind(content.stack()
                                    .getType()
                                    .name())
                            .bind(toJson(content.stack()))
                            .bind(content.amount())
                            .bind(content.price()));
            conf.query(query)
                    .batch(calls)
                    .insert();

            query = """
                    INSERT INTO order_states(id, state) VALUES(?, ?)""";
            conf.query(query)
                    .single(call().bind(orderId).bind(OrderState.UNCLAIMED.stateId()))
                    .update();
        }
    }

    @Override
    protected void updateOrderState(SimpleOrder order, OrderState state) {
        @Language("mariadb")
        var query = """
                UPDATE order_states
                SET state = ?
                WHERE id = ?
                                """;
        query(query)
                .single(call().bind(state.stateId()).bind(order.id()))
                .update();
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
                    created
                FROM
                    order_states s
                        LEFT JOIN orders o
                        ON o.id = s.id
                WHERE last_update < NOW() - INTERVAL ? HOUR
                  AND company IS NOT NULL
                  AND state = ?
                ORDER BY last_update""";
        return query(query)
                .single(call().bind(hours)
                        .bind(OrderState.CLAIMED.stateId()))
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
                    created
                FROM
                    order_states s
                        LEFT JOIN orders o
                        ON o.id = s.id
                WHERE last_update < NOW() - INTERVAL ? HOUR
                  AND company IS NOT NULL
                  AND state = ?
                ORDER BY last_update""";
        return query(query)
                .single(call().bind(hours).bind(OrderState.UNCLAIMED.stateId()))
                .map(this::buildSimpleOrder)
                .all();
    }

    @Override
    protected List<SimpleOrder> getExpiredOrdersByCompany(int hours, SimpleCompany company) {
        @Language("mariadb")
        var query = """
                SELECT
                    o.id,
                    last_update,
                    company,
                    state,
                    owner_uuid,
                    name,
                    created
                FROM
                    order_states s
                        LEFT JOIN orders o
                        ON o.id = s.id
                WHERE last_update < NOW() - INTERVAL ? HOUR
                  AND company = ?
                  AND state = ?
                ORDER BY last_update
                """;
        return query(query)
                .single(call().bind(hours)
                        .bind(company.id())
                        .bind(OrderState.CLAIMED.stateId()))
                .map(this::buildSimpleOrder)
                .all();
    }

    @Override
    protected boolean claimOrder(SimpleCompany company, SimpleOrder order) {
        @Language("mariadb")
        var query = """
                UPDATE order_states
                SET state       = ?,
                    company     = ?,
                    last_update = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND state = ?""";
        return query(query)
                .single(call().bind(OrderState.CLAIMED.stateId())
                        .bind(company.id())
                        .bind(order.id())
                        .bind(OrderState.UNCLAIMED.stateId()))
                .update()
                .changed();
    }

    @Override
    protected void orderDelivered(SimpleOrder order) {
        @Language("mariadb")
        var update = """
                UPDATE order_states
                SET state       = ?,
                    last_update = CURRENT_TIMESTAMP
                WHERE id = ?""";
        @Language("mariadb")
        var delete = """
                DELETE FROM orders_delivered WHERE id = ?""";
        try (var conf = QueryConfiguration.getDefault().withSingleTransaction()) {

            conf.query(update)
                    .single(call().bind(OrderState.DELIVERED.stateId()).bind(order.id()))
                    .update();

            conf.query(delete)
                    .single(call().bind(order.id()))
                    .delete();
        }
    }

    @Override
    protected void unclaimOrder(SimpleOrder order) {
        @Language("mariadb")
        var query = """
                UPDATE order_states
                SET state       = ?,
                    company     = NULL,
                    last_update = CURRENT_TIMESTAMP
                WHERE id = ?""";
        @Language("mariadb")
        var delete = """
                DELETE FROM orders_delivered WHERE id = ?""";
        try (var conf = QueryConfiguration.getDefault().withSingleTransaction()) {
            conf.query(query)
                    .single(call().bind(OrderState.UNCLAIMED.stateId()).bind(order.id()))
                    .update();
            conf.query(delete)
                    .single(call().bind(order.id()))
                    .delete();
        }
    }

    @Override
    protected void deliver(OfflinePlayer player, SimpleOrder order, Material material, int amount) {
        @Language("mariadb")
        var query = """
                INSERT
                INTO orders_delivered(id, worker_uuid, material, delivered)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE delivered = delivered + VALUES(delivered)""";
        query(query)
                .single(call().bind(order.id())
                        .bind(player.getUniqueId(), UUID_BYTES)
                        .bind(material.name())
                        .bind(amount))
                .update();
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
                    state
                FROM
                    orders o
                        LEFT JOIN order_states oc
                        ON o.id = oc.id
                WHERE o.id = ?""";
        return query(query)
                .single(call().bind(id))
                .map(this::buildSimpleOrder)
                .first();
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
                    state
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
                    state
                FROM
                    orders o
                        LEFT JOIN order_states oc
                        ON o.id = oc.id
                WHERE oc.company = ?
                  AND state >= ?
                  AND state <= ?""";
        return query(query)
                .single(call().bind(company.id())
                        .bind(min.stateId())
                        .bind(max.stateId()))
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
                    state
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
                .single(call().bind(player.getUniqueId(), UUID_BYTES)
                        .bind(OrderState.DELIVERED.stateId()))
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
                .single(call().bind(company.id())
                        .bind(OrderState.CLAIMED.stateId()))
                .map(rs -> rs.getInt("count"))
                .first()
                .get();
    }

    @Override
    protected List<OrderContent> getOrderContent(SimpleOrder order) {
        @Language("mariadb")
        var query = """
                SELECT material, stack, amount, price
                FROM order_content
                WHERE id = ?""";
        var orderContents = query(query)
                .single(call().bind(order.id()))
                .map(row -> new OrderContent(toItemStack(row.getString("stack")), row.getInt("amount"), row.getDouble("price")))
                .all();

        for (var orderContent : orderContents) {
            orderContent.parts(getContentParts(order, orderContent.stack()
                    .getType()));
        }
        return orderContents;
    }

    @Override
    protected List<ContentPart> getContentParts(SimpleOrder order, Material material) {
        @Language("mariadb")
        var query = """
                SELECT worker_uuid, delivered
                FROM orders_delivered
                WHERE id = ?
                  AND material = ?""";
        return query(query)
                .single(call().bind(order.id())
                        .bind(material.name()))
                .map(row -> new ContentPart(row.get("worker_uuid", UUID_BYTES), row.getInt("delivered")))
                .all();
    }

    @Override
    protected void purgeCompanyOrders(SimpleCompany profile) {
        for (var simpleOrder : ordersByCompany(profile, OrderState.CLAIMED, OrderState.CLAIMED)) {
            unclaimOrder(simpleOrder);
        }
    }

    @Override
    protected void purgeOrder(SimpleOrder order) {
        @Language("mariadb")
        var query = """
                DELETE
                FROM orders
                WHERE id = ?""";
        query(query)
                .single(call().bind(order.id()))
                .delete();
    }

    @Override
    protected void deleteOrder(SimpleOrder order) {
        @Language("mariadb")
        var query = """
                DELETE
                FROM orders
                WHERE id = ?""";
        query(query)
                .single(call().bind(order.id()))
                .update();
    }

    @Override
    protected List<FullOrder> getOrdersByQuery(SearchQuery searchQuery, OrderState min, OrderState max) {
        @Language("mariadb")
        var query = """
                SELECT o.id, o.owner_uuid, o.name, o.created, os.company, os.last_update, os.state
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
                .map(rs -> new MaterialPrice(material.toLowerCase(Locale.ROOT), rs.getDouble("avg_price"),
                        rs.getDouble("min_price"), rs.getDouble("max_price")))
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
                            WHERE s.state >= 200) c
                      WHERE c.id < 100
                      GROUP BY c.material) avg
                WHERE TRUE
                ON DUPLICATE KEY UPDATE avg_price = avg.avg_price;""";
        query(query).single()
                .update();
    }
}
