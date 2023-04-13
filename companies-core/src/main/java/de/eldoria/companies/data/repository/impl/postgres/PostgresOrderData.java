/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.eldoria.companies.commands.company.order.search.SearchQuery;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbOrderData;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.intellij.lang.annotations.Language;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.ExecutorService;
import static de.eldoria.companies.data.StaticQueryAdapter.builder;

public class PostgresOrderData extends MariaDbOrderData {
    /**
     * Create a new QueryBuilderFactory
     *
     * @param executorService executor service for future handling
     * @param mapper
     */
    public PostgresOrderData(ExecutorService executorService, ObjectMapper mapper) {
        super(executorService, mapper);
    }

    @Override
    protected List<SimpleOrder> getExpiredOrders(int hours) {
        @Language("postgresql")
        var query = """
                SELECT o.id,
                       last_update,
                       company,
                       state,
                       owner_uuid,
                       name,
                       created
                FROM order_states s
                         LEFT JOIN orders o
                                   ON o.id = s.id
                WHERE last_update < now() - (? || ' HOUR')::INTERVAL
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
    protected List<SimpleOrder> getExpiredOrdersByCompany(int hours, SimpleCompany company) {
        @Language("postgresql")
        var query = """
                SELECT o.id,
                       last_update,
                       company,
                       state,
                       owner_uuid,
                       name,
                       created
                FROM order_states s
                         LEFT JOIN orders o
                                   ON o.id = s.id
                WHERE last_update < now() - (? || ' HOUR')::INTERVAL
                  AND company = ?
                  AND state = ?
                ORDER BY last_update""";
        return builder(SimpleOrder.class)
                .query(query)
                .parameter(stmt -> stmt.setInt(hours)
                                       .setInt(company.id())
                                       .setInt(OrderState.CLAIMED.stateId()))
                .readRow(this::buildSimpleOrder)
                .allSync();
    }

    @Override
    protected void deliver(OfflinePlayer player, SimpleOrder order, Material material, int amount) {
        @Language("postgresql")
        var query = """
                INSERT
                INTO orders_delivered(id, worker_uuid, material, delivered)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(id, worker_uuid, material) DO UPDATE SET delivered = delivered + excluded.delivered""";
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
    protected List<FullOrder> getOrdersByQuery(SearchQuery searchQuery, OrderState min, OrderState max) {
        @Language("postgresql")
        var query = """
                SELECT o.id, o.owner_uuid, o.name, o.created, os.company, os.last_update, os.state
                FROM orders o
                         LEFT JOIN (SELECT c.id, string_agg(c.material, ' ') AS materials, sum(amount) AS amount, sum(price) AS price
                                    FROM order_content c
                                    GROUP BY id) oc
                                   ON o.id = oc.id
                         LEFT JOIN order_states os
                                   ON o.id = os.id
                WHERE o.name ~~* ?
                  AND oc.materials ~* ?
                  AND oc.price >= ?
                  AND oc.price <= ?
                  AND oc.amount >= ?
                  AND oc.amount <= ?
                  AND os.state >= ?
                  AND os.state <= ?""";
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
    public void refreshMaterialPrices() {
        @Language("postgresql")
        var query = """
                REFRESH MATERIALIZED VIEW material_price;""";
        builder()
                .queryWithoutParams(query)
                .update()
                .sendSync();
    }
}
