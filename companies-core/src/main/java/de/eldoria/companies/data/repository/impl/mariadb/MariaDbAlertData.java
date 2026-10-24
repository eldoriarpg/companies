/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.chojo.sadu.queries.api.configuration.ConnectedQueryConfiguration;
import de.eldoria.companies.data.repository.AAlertData;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.repository.impl.Transactions;
import de.eldoria.companies.data.wrapper.alert.AlertResult;
import de.eldoria.companies.data.wrapper.alert.HitResult;
import de.eldoria.companies.data.wrapper.alert.OrderAlert;
import de.eldoria.companies.orders.query.OrderFilter;
import org.intellij.lang.annotations.Language;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * Order alerts for MariaDB. The other dialects override the company lock and the binding of times.
 */
public class MariaDbAlertData extends AAlertData {
    @Language("mariadb")
    private static final String ALERTS = """
            SELECT id, company_id, name, filter, creator_uuid, enabled, created, last_notified, pending
            FROM order_alerts
            """;

    public MariaDbAlertData(ObjectMapper mapper, ExecutorService executor) {
        super(mapper, executor);
    }

    /**
     * Binds a point in time in the form the time columns of this dialect compare correctly.
     *
     * @return adapter for times
     */
    protected Adapter<LocalDateTime> time() {
        return TimeColumns.TIMESTAMP;
    }

    /**
     * Reads a time column.
     *
     * @param row    row
     * @param column name of the column
     * @return time
     * @throws SQLException when the column is missing
     */
    protected LocalDateTime readTime(Row row, String column) throws SQLException {
        return row.getTimestamp(column).toLocalDateTime();
    }

    /**
     * Reads a time column that may be null.
     *
     * @param row    row
     * @param column name of the column
     * @return time, or empty when the column is null
     * @throws SQLException when the column is missing
     */
    protected Optional<LocalDateTime> readNullableTime(Row row, String column) throws SQLException {
        var timestamp = row.getTimestamp(column);
        return timestamp == null ? Optional.empty() : Optional.of(timestamp.toLocalDateTime());
    }

    /**
     * Locks the row of a company until the transaction ends, so its alerts cannot be counted twice at the same time.
     *
     * @param conf    transaction
     * @param company id of the company
     * @return true when the company exists
     */
    protected boolean lockCompany(ConnectedQueryConfiguration conf, int company) {
        @Language("mariadb")
        var query = """
                SELECT id FROM companies WHERE id = ? FOR UPDATE""";
        return conf.query(query)
                .single(call().bind(company))
                .map(row -> row.getInt("id"))
                .first()
                .isPresent();
    }

    @Override
    protected AlertResult saveAlert(
            int company, String name, OrderFilter filter, UUID creator, int maxAlerts, LocalDateTime now) {
        @Language("mariadb")
        var names = """
                SELECT name FROM order_alerts WHERE company_id = ?""";
        @Language("mariadb")
        var insert = """
                INSERT INTO order_alerts(company_id, name, filter, creator_uuid, enabled, created, pending)
                VALUES (?, ?, ?, ?, TRUE, ?, 0)""";
        var errors = new ArrayList<SQLException>();
        var conf = Transactions.open(errors);
        try (conf) {
            var exists = lockCompany(conf, company);
            var taken = conf.query(names)
                    .single(call().bind(company))
                    .map(row -> row.getString("name"))
                    .all();
            if (!errors.isEmpty()) return failed(conf, errors, AlertResult.FAILED);
            if (!exists) return AlertResult.FAILED;
            if (taken.contains(name)) return AlertResult.NAME_TAKEN;
            if (taken.size() >= maxAlerts) return AlertResult.LIMIT_REACHED;
            conf.query(insert)
                    .single(call().bind(company)
                            .bind(name)
                            .bind(toJson(filter))
                            .bind(creator, UUID_BYTES)
                            .bind(now, time()))
                    .insert();
            if (!errors.isEmpty()) return failed(conf, errors, AlertResult.NAME_TAKEN);
        }
        return AlertResult.CREATED;
    }

    @Override
    protected List<OrderAlert> getByCompany(int company) {
        return query(ALERTS + "WHERE company_id = ? ORDER BY name, id")
                .single(call().bind(company))
                .map(this::parseAlert)
                .all();
    }

    @Override
    protected List<OrderAlert> getEnabled() {
        return query(ALERTS + "WHERE enabled ORDER BY company_id, name, id")
                .single()
                .map(this::parseAlert)
                .all();
    }

    @Override
    protected boolean remove(int company, String name) {
        @Language("mariadb")
        var query = """
                DELETE FROM order_alerts WHERE company_id = ? AND name = ?""";
        return query(query).single(call().bind(company).bind(name)).delete().changed();
    }

    @Override
    protected boolean saveEnabled(int company, String name, boolean enabled) {
        @Language("mariadb")
        var query = """
                UPDATE order_alerts SET enabled = ? WHERE company_id = ? AND name = ?""";
        return query(query)
                .single(call().bind(enabled).bind(company).bind(name))
                .update()
                .changed();
    }

    @Override
    protected HitResult hit(int alert, LocalDateTime now, LocalDateTime notifiedBefore) {
        @Language("mariadb")
        var notify = """
                UPDATE order_alerts
                SET last_notified = ?,
                    pending       = 0
                WHERE id = ?
                  AND enabled
                  AND (last_notified IS NULL OR last_notified <= ?)""";
        @Language("mariadb")
        var suppress = """
                UPDATE order_alerts
                SET pending = pending + 1
                WHERE id = ?
                  AND enabled""";
        var notified = query(notify)
                .single(call().bind(now, time()).bind(alert).bind(notifiedBefore, time()))
                .update()
                .changed();
        if (notified) return HitResult.NOTIFY;
        var counted = query(suppress).single(call().bind(alert)).update().changed();
        return counted ? HitResult.SUPPRESSED : HitResult.GONE;
    }

    @Override
    protected List<OrderAlert> getDigestDue(LocalDateTime notifiedBefore) {
        return query(ALERTS + "WHERE enabled AND pending > 0 AND last_notified <= ? ORDER BY last_notified, id")
                .single(call().bind(notifiedBefore, time()))
                .map(this::parseAlert)
                .all();
    }

    @Override
    protected boolean claimDigest(OrderAlert alert, LocalDateTime now) {
        @Language("mariadb")
        var query = """
                UPDATE order_alerts
                SET pending       = 0,
                    last_notified = ?
                WHERE id = ?
                  AND pending = ?""";
        return query(query)
                .single(call().bind(now, time()).bind(alert.id()).bind(alert.pending()))
                .update()
                .changed();
    }

    private static AlertResult failed(
            ConnectedQueryConfiguration conf, List<SQLException> errors, AlertResult violation) {
        return Transactions.rollback(conf, errors) ? violation : AlertResult.FAILED;
    }

    private OrderAlert parseAlert(Row row) throws SQLException {
        return new OrderAlert(
                row.getInt("id"),
                row.getInt("company_id"),
                row.getString("name"),
                toFilter(row.getString("filter")),
                row.get("creator_uuid", UUID_BYTES),
                row.getBoolean("enabled"),
                readTime(row, "created"),
                readNullableTime(row, "last_notified"),
                row.getInt("pending"));
    }
}
