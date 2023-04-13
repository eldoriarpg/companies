/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.bukkit.plugin.Plugin;
import org.intellij.lang.annotations.Language;
import static de.eldoria.companies.data.StaticQueryAdapter.builder;

import javax.sql.DataSource;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class MariaDbOrderData extends AOrderData {

    /**
     * Create a new QueryBuilderFactory
     *
     * @param executorService executor service for future handling
     * @param mapper
     */
    public MariaDbOrderData(ExecutorService executorService, ObjectMapper mapper) {
        super(executorService, mapper);
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
        var orderId = builder(Integer.class)
                .query(query)
                .parameter(stmt -> stmt.setUuidAsBytes(player.getUniqueId())
                                       .setString(order.name()))
                .readRow(rs -> rs.getInt(1))
                .firstSync()
                .get();

        var builder = builder();
        for (var content : order.contents()) {
            query = """
                    INSERT INTO order_content(id, material, stack, amount, price)
                    VALUES (?, ?, ?, ?, ?)""";
            builder.query(query)
                   .parameter(stmt -> stmt.setInt(orderId)
                                          .setString(content.stack()
                                                            .getType()
                                                            .name())
                                          .setString(toJson(content.stack()))
                                          .setInt(content.amount())
                                          .setDouble(content.price()))
                   .append();
        }
        query = """
                INSERT INTO order_states(id, state) VALUES(?, ?)""";
        builder.query(query)
               .parameter(stmt -> stmt.setInt(orderId)
                                      .setInt(OrderState.UNCLAIMED.stateId()))
               .update()
               .sendSync();
    }

    @Override
    protected void updateOrderState(SimpleOrder order, OrderState state) {
        @Language("mariadb")
        var query = """
                UPDATE order_states
                SET state = ?
                WHERE id = ?
                                """;
        builder()
                .query(query)
                .parameter(stmt -> stmt.setInt(state.stateId())
                                       .setInt(order.id()))
                .update()
                .sendSync();
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
        return builder(SimpleOrder.class)
                .query(query)
                .parameter(stmt -> stmt.setInt(hours)
                                       .setInt(OrderState.CLAIMED.stateId()))
                .readRow(this::buildSimpleOrder)
                .allSync();
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
        return builder(SimpleOrder.class)
                .query(query)
                .parameter(stmt -> stmt.setInt(hours)
                                       .setInt(OrderState.UNCLAIMED.stateId()))
                .readRow(this::buildSimpleOrder)
                .allSync();
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
        return builder(SimpleOrder.class)
                .query(query)
                .parameter(stmt -> stmt.setInt(hours)
                                       .setInt(company.id())
                                       .setInt(OrderState.CLAIMED.stateId()))
                .readRow(this::buildSimpleOrder)
                .allSync();
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
        return builder()
                .query(query)
                .parameter(stmt -> stmt.setInt(OrderState.CLAIMED.stateId())
                                       .setInt(company.id())
                                       .setInt(order.id())
                                       .setInt(OrderState.UNCLAIMED.stateId()))
                .update()
                .sendSync()
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
        builder()
                .query(update)
                .parameter(stmt -> stmt.setInt(OrderState.DELIVERED.stateId())
                                       .setInt(order.id()))
                .append()
                .query(delete)
                .parameter(stmt -> stmt.setInt(order.id()))
                .update()
                .sendSync();
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
        builder()
                .query(query)
                .parameter(stmt -> stmt.setInt(OrderState.UNCLAIMED.stateId())
                                       .setInt(order.id()))
                .append()
                .query(delete)
                .parameter(stmt -> stmt.setInt(order.id()))
                .update()
                .sendSync();
    }

    @Override
    protected void deliver(OfflinePlayer player, SimpleOrder order, Material material, int amount) {
        @Language("mariadb")
        var query = """
                INSERT
                INTO orders_delivered(id, worker_uuid, material, delivered)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE delivered = delivered + VALUES(delivered)""";
        builder()
                .query(query)
                .parameter(stmt -> stmt.setInt(order.id())
                                       .setUuidAsBytes(player.getUniqueId())
                                       .setString(material.name())
                                       .setInt(amount))
                .update()
                .sendSync();
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
        return builder(SimpleOrder.class)
                .query(query)
                .parameter(stmt -> stmt.setInt(id))
                .readRow(this::buildSimpleOrder)
                .firstSync();
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
        return builder(SimpleOrder.class)
                .query(query)
                .parameter(stmt -> stmt.setInt(id)
                                       .setInt(company))
                .readRow(this::buildSimpleOrder)
                .firstSync();
    }

    @Override
    protected CompletableFuture<List<SimpleOrder>> ordersByCompany(ISimpleCompany company, OrderState min, OrderState max) {
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
        return builder(SimpleOrder.class)
                .query(query)
                .parameter(stmt -> stmt.setInt(company.id())
                                       .setInt(min.stateId())
                                       .setInt(max.stateId()))
                .readRow(this::buildSimpleOrder)
                .all(executorService());
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
        return builder(SimpleOrder.class)
                .query(query)
                .parameter(stmt -> stmt.setUuidAsBytes(player.getUniqueId())
                                       .setInt(min.stateId())
                                       .setInt(max.stateId()))
                .readRow(this::buildSimpleOrder)
                .allSync();
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
        return builder(Integer.class)
                .query(query)
                .parameter(stmt -> stmt.setUuidAsBytes(player.getUniqueId())
                                       .setInt(OrderState.DELIVERED.stateId()))
                .readRow(rs -> rs.getInt("count"))
                .firstSync()
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
        return builder(Integer.class)
                .query(query)
                .parameter(stmt -> stmt.setInt(company.id())
                                       .setInt(OrderState.CLAIMED.stateId()))
                .readRow(rs -> rs.getInt("count"))
                .firstSync()
                .get();
    }

    @Override
    protected List<OrderContent> getOrderContent(SimpleOrder order) {
        @Language("mariadb")
        var query = """
                SELECT material, stack, amount, price
                FROM order_content
                WHERE id = ?""";
        var orderContents = builder(OrderContent.class)
                .query(query)
                .parameter(stmt -> stmt.setInt(order.id()))
                .readRow(row -> new OrderContent(toItemStack(row.getString("stack")), row.getInt("amount"), row.getDouble("price")))
                .allSync();

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
        return builder(ContentPart.class)
                .query(query)
                .parameter(stmt -> stmt.setInt(order.id())
                                       .setString(material.name()))
                .readRow(row -> new ContentPart(row.getUuidFromBytes("worker_uuid"), row.getInt("delivered")))
                .allSync();
    }

    @Override
    protected void purgeCompanyOrders(SimpleCompany profile) {
        for (var simpleOrder : ordersByCompany(profile, OrderState.CLAIMED, OrderState.CLAIMED).join()) {
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
        builder().query(query)
                 .parameter(stmt -> stmt.setInt(order.id()))
                 .delete()
                 .sendSync();
    }

    @Override
    protected void deleteOrder(SimpleOrder order) {
        @Language("mariadb")
        var query = """
                DELETE
                FROM orders
                WHERE id = ?""";
        builder()
                .query(query)
                .parameter(stmt -> stmt.setInt(order.id()))
                .update()
                .sendSync();
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
        var orders = builder(SimpleOrder.class)
                .query(query)
                .parameter(stmt -> stmt.setString("%" + searchQuery.name() + "%")
                                       .setString(searchQuery.materialRegex())
                                       .setDouble(searchQuery.minPrice())
                                       .setDouble(searchQuery.maxPrice())
                                       .setInt(searchQuery.minOrderSize())
                                       .setInt(searchQuery.maxOrderSize())
                                       .setInt(min.stateId())
                                       .setInt(max.stateId()))
                .readRow(this::buildSimpleOrder)
                .allSync();
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
        return builder(MaterialPrice.class)
                .query(query)
                .parameter(stmt -> stmt.setString(material.toUpperCase(Locale.ROOT)))
                .readRow(rs -> new MaterialPrice(material.toLowerCase(Locale.ROOT), rs.getDouble("avg_price"),
                        rs.getDouble("min_price"), rs.getDouble("max_price")))
                .firstSync();
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
        builder()
                .queryWithoutParams(query)
                .update()
                .sendSync();
    }
}
