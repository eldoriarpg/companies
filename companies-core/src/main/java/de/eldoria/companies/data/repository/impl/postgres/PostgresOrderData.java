/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.postgres;

import de.chojo.sadu.queries.api.configuration.ConnectedQueryConfiguration;
import de.eldoria.companies.commands.company.order.search.SearchQuery;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbOrderData;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.util.Times;
import org.intellij.lang.annotations.Language;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
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
    protected List<SimpleOrder> expiredClaims(int hours, String claimant) {
        @Language("postgresql")
        var query = """
                SELECT o.id,
                       last_update,
                       company,
                       state,
                       owner_uuid,
                       name,
                       created,
                       o.fee,
                       extensions,
                       delivery_bonus,
                       series_id,
                       solo_worker
                FROM order_states s
                         LEFT JOIN orders o
                                   ON o.id = s.id
                WHERE last_update < CAST(? AS TIMESTAMP) - ((? + delivery_bonus) || ' HOUR')::INTERVAL
                  AND %s IS NOT NULL
                  AND state = ?
                ORDER BY last_update""";
        return query(query, claimant)
                .single(call().bind(Times.now(), time()).bind(hours).bind(OrderState.CLAIMED.stateId()))
                .map(this::buildSimpleOrder)
                .all();
    }

    @Override
    protected List<SimpleOrder> getDeadOrders(int hours) {
        @Language("postgresql")
        var query = """
                SELECT o.id,
                       last_update,
                       company,
                       state,
                       owner_uuid,
                       name,
                       created,
                       o.fee,
                       extensions,
                       delivery_bonus,
                       series_id,
                       solo_worker
                FROM order_states s
                         JOIN orders o
                              ON o.id = s.id
                WHERE last_update < CAST(? AS TIMESTAMP) - (? || ' HOUR')::INTERVAL
                  AND state = ?
                ORDER BY last_update""";
        return query(query)
                .single(call().bind(Times.now(), time()).bind(hours).bind(OrderState.UNCLAIMED.stateId()))
                .map(this::buildSimpleOrder)
                .all();
    }

    @Override
    protected List<SimpleOrder> getPurgeWarnings(int hours) {
        @Language("postgresql")
        var query = """
                SELECT o.id,
                       last_update,
                       company,
                       state,
                       owner_uuid,
                       name,
                       created,
                       o.fee,
                       extensions,
                       delivery_bonus,
                       series_id,
                       solo_worker
                FROM order_states s
                         JOIN orders o
                              ON o.id = s.id
                WHERE last_update < CAST(? AS TIMESTAMP) - (? || ' HOUR')::INTERVAL
                  AND state = ?
                  AND (purge_warning IS NULL OR purge_warning <= last_update)
                ORDER BY last_update""";
        return query(query)
                .single(call().bind(Times.now(), time()).bind(hours).bind(OrderState.UNCLAIMED.stateId()))
                .map(this::buildSimpleOrder)
                .all();
    }

    @Override
    protected List<FullOrder> getOrdersByQuery(SearchQuery searchQuery, OrderState min, OrderState max) {
        @Language("postgresql")
        var query = """
                SELECT o.id, o.owner_uuid, o.name, o.created, os.company, os.last_update, os.state, o.fee, os.extensions, os.delivery_bonus, o.series_id, os.solo_worker
                FROM orders o
                         LEFT JOIN (SELECT c.id, sum(amount) AS amount, sum(price) AS price
                                    FROM order_content c
                                    GROUP BY id) oc
                                   ON o.id = oc.id
                         LEFT JOIN (SELECT m.id, string_agg(m.material, ' ') AS materials
                                    FROM (SELECT id, material FROM order_content WHERE criteria IS NULL
                                          UNION ALL
                                          SELECT id, material FROM order_line_materials) m
                                    GROUP BY m.id) om
                                   ON o.id = om.id
                         LEFT JOIN order_states os
                                   ON o.id = os.id
                WHERE o.name ~~* ?
                  AND COALESCE(om.materials, '') ~* ?
                  AND oc.price >= ?
                  AND oc.price <= ?
                  AND oc.amount >= ?
                  AND oc.amount <= ?
                  AND os.state >= ?
                  AND os.state <= ?""";
        var orders = query(query)
                .single(call().bind("%" + searchQuery.name() + "%")
                        .bind(searchQuery.materialRegex("\\y"))
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
    protected boolean extendUnclaimed(SimpleOrder order, int hours) {
        @Language("postgresql")
        var query = """
                UPDATE order_states
                SET last_update = last_update + (? || ' HOUR')::INTERVAL
                WHERE id = ?
                  AND state = ?""";
        return moveLastUpdate(query, order, hours);
    }

    @Override
    protected void lockSoloWorker(ConnectedQueryConfiguration conf, UUID player) {
        @Language("postgresql")
        var insert = """
                INSERT INTO solo_cooldowns(worker_uuid, ends_at) VALUES (?, ?)
                ON CONFLICT (worker_uuid) DO NOTHING""";
        insertSoloWorker(conf, insert, player);
        @Language("postgresql")
        var lock = """
                SELECT worker_uuid
                FROM solo_cooldowns
                WHERE worker_uuid = ?
                    FOR UPDATE""";
        conf.query(lock)
                .single(call().bind(player, UUID_BYTES))
                .map(row -> row.getBytes("worker_uuid"))
                .first();
    }

    @Override
    public void refreshMaterialPrices() {
        @Language("postgresql")
        var query = """
                REFRESH MATERIALIZED VIEW material_price;""";
        query(query).single().update();
    }
}
