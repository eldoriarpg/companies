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
import org.intellij.lang.annotations.Language;

import java.util.List;
import java.util.concurrent.ExecutorService;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

public class PostgresOrderData extends MariaDbOrderData {
    /**
     * Create a new QueryBuilderFactory
     *
     * @param executorService executor service for future handling
     * @param mapper
     */
    public PostgresOrderData(ExecutorService executorService, ObjectMapper mapper) {
        super(mapper);
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
        return query(query)
                .single(call().bind(hours).bind(OrderState.CLAIMED.stateId()))
                .map(this::buildSimpleOrder)
                .all();
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
        return query(query)
                .single(call().bind(hours)
                        .bind(company.id())
                        .bind(OrderState.CLAIMED.stateId()))
                .map(this::buildSimpleOrder)
                .all();
    }

    @Override
    protected void deliver(OfflinePlayer player, SimpleOrder order, Material material, int amount) {
        @Language("postgresql")
        var query = """
                INSERT
                INTO orders_delivered(id, worker_uuid, material, delivered)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(id, worker_uuid, material) DO UPDATE SET delivered = delivered + excluded.delivered""";
        query(query)
                .single(call().bind(order.id())
                        .bind(player.getUniqueId(), UUID_BYTES)
                        .bind(material.name())
                        .bind(amount))
                .update();
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
    public void refreshMaterialPrices() {
        @Language("postgresql")
        var query = """
                REFRESH MATERIALIZED VIEW material_price;""";
        query(query).single()
                .update();
    }
}
