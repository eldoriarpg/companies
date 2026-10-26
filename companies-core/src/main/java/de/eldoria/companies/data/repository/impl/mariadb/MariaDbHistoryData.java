/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.eldoria.companies.components.order.HistoryOutcome;
import de.eldoria.companies.data.repository.AHistoryData;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.wrapper.history.HistoryEntry;
import de.eldoria.companies.orders.SystemOwner;
import org.intellij.lang.annotations.Language;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * The order history for MariaDB. The other dialects override the JSON parameter and the binding of times.
 */
public class MariaDbHistoryData extends AHistoryData {
    @Language("mariadb")
    private static final String ENTRIES = """
            SELECT id, order_id, owner_uuid, company_id, outcome, order_name, placed, finished, snapshot
            FROM order_history
            """;

    /**
     * @param mapper   mapper for snapshots, able to read and write item stacks
     * @param executor executor of the futures
     */
    public MariaDbHistoryData(ObjectMapper mapper, ExecutorService executor) {
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
        return row.getObject(column, LocalDateTime.class);
    }

    /**
     * The parameter of a JSON value in an insert.
     *
     * @return parameter, possibly with a cast
     */
    protected String jsonParameter() {
        return "?";
    }

    @Override
    protected Optional<Long> saveEntry(HistoryEntry entry) {
        @Language("mariadb")
        var query = """
                INSERT INTO order_history(order_id, owner_uuid, company_id, outcome, order_name, placed, finished, snapshot)
                VALUES (?, ?, ?, ?, ?, ?, ?, %s)
                RETURNING id""";
        return query(query, jsonParameter())
                .single(call().bind(entry.order())
                        .bind(entry.owner(), UUID_BYTES)
                        .bind(entry.company())
                        .bind(entry.outcome().id())
                        .bind(entry.name())
                        .bind(entry.placed(), time())
                        .bind(entry.finished(), time())
                        .bind(toJson(entry.snapshot())))
                .map(row -> row.getLong(1))
                .first();
    }

    @Override
    protected List<HistoryEntry> getOwnerHistory(UUID owner, int page, int size) {
        return query(ENTRIES + "WHERE owner_uuid = ? ORDER BY finished DESC, id DESC LIMIT ? OFFSET ?")
                .single(call().bind(owner, UUID_BYTES).bind(size).bind((page - 1) * size))
                .map(this::parseEntry)
                .all();
    }

    @Override
    protected List<HistoryEntry> getCompanyHistory(int company, int page, int size) {
        return query(ENTRIES + "WHERE company_id = ? ORDER BY finished DESC, id DESC LIMIT ? OFFSET ?")
                .single(call().bind(company).bind(size).bind((page - 1) * size))
                .map(this::parseEntry)
                .all();
    }

    @Override
    protected Optional<HistoryEntry> getEntry(long id) {
        return query(ENTRIES + "WHERE id = ?")
                .single(call().bind(id))
                .map(this::parseEntry)
                .first();
    }

    @Override
    protected List<HistoryEntry> getByOrder(int order) {
        return query(ENTRIES + "WHERE order_id = ? ORDER BY finished, id")
                .single(call().bind(order))
                .map(this::parseEntry)
                .all();
    }

    @Override
    protected List<HistoryEntry> getCompanyEntries(HistoryOutcome outcome, LocalDateTime from, LocalDateTime to) {
        return query(ENTRIES
                        + "WHERE company_id IS NOT NULL AND outcome = ? AND finished >= ? AND finished < ? "
                        + "AND NOT EXISTS (SELECT 1 FROM order_history o WHERE o.order_id = order_history.order_id "
                        + "AND o.owner_uuid = ?) "
                        + "ORDER BY finished, id")
                .single(call().bind(outcome.id())
                        .bind(from, time())
                        .bind(to, time())
                        .bind(SystemOwner.ID, UUID_BYTES))
                .map(this::parseEntry)
                .all();
    }

    @Override
    protected int purgeBefore(LocalDateTime before) {
        @Language("mariadb")
        var query = """
                DELETE
                FROM order_history
                WHERE finished < ?""";
        @Language("mariadb")
        var decisions = """
                DELETE
                FROM system_order_decisions
                WHERE decided_at < ?
                  AND order_id NOT IN (SELECT id FROM orders)""";
        query(decisions).single(call().bind(before, time())).delete();
        return query(query).single(call().bind(before, time())).delete().rows();
    }

    private HistoryEntry parseEntry(Row row) throws SQLException {
        var company = row.getObject("company_id") instanceof Number number ? number.intValue() : null;
        var outcome = row.getInt("outcome");
        return new HistoryEntry(
                row.getLong("id"),
                row.getInt("order_id"),
                row.get("owner_uuid", UUID_BYTES),
                company,
                HistoryOutcome.byId(outcome).orElseThrow(() -> new SQLException("Unknown history outcome " + outcome)),
                row.getString("order_name"),
                readTime(row, "placed"),
                readTime(row, "finished"),
                toSnapshot(row.getString("snapshot")));
    }
}
