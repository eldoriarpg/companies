/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.data.repository.AServerEventData;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.repository.impl.Transactions;
import de.eldoria.companies.data.wrapper.event.EventKind;
import de.eldoria.companies.data.wrapper.event.EventPayout;
import de.eldoria.companies.data.wrapper.event.EventState;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * Server events for MariaDB. PostgreSQL and SQLite run the same statements. The scopes are stored as comma separated
 * lists, null when empty, since they are only matched on the nodes and never searched in the database.
 */
public class MariaDbServerEventData extends AServerEventData {
    private static final double BUDGET_TOLERANCE = 0.000001;

    @Language("mariadb")
    private static final String EVENT = """
            SELECT id, name, kind, value, scope_materials, scope_levels, starts, ends, funding, account, budget, spent,
                   state, template, created_by, created, closed, closed_by
            FROM server_events
            """;

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
     * @return time, null when the column is null
     * @throws SQLException when the column is missing
     */
    protected @Nullable LocalDateTime readTime(Row row, String column) throws SQLException {
        return row.getObject(column, LocalDateTime.class);
    }

    @Override
    public Optional<Integer> create(ServerEvent event) {
        @Language("mariadb")
        var insert = """
                INSERT INTO server_events(name, kind, value, scope_materials, scope_levels, starts, ends, funding,
                                          account, budget, spent, state, template, created_by, created)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)
                RETURNING id""";
        var funding = event.funding();
        return query(insert)
                .single(call().bind(event.name())
                        .bind(event.kind().name())
                        .bind(event.value())
                        .bind(join(event.materials()))
                        .bind(join(event.levels()))
                        .bind(event.starts(), time())
                        .bind(event.ends(), time())
                        .bind(funding == null ? null : funding.name())
                        .bind(event.account())
                        .bind(event.budget())
                        .bind(event.state().name())
                        .bind(event.template())
                        .bind(event.createdBy())
                        .bind(event.created(), time()))
                .map(row -> row.getInt(1))
                .first();
    }

    @Override
    public Optional<ServerEvent> event(int id) {
        return query(EVENT + "WHERE id = ?")
                .single(call().bind(id))
                .map(this::parse)
                .first();
    }

    @Override
    public List<ServerEvent> events(int limit) {
        return query(EVENT + "ORDER BY id DESC LIMIT ?")
                .single(call().bind(limit))
                .map(this::parse)
                .all();
    }

    @Override
    public List<ServerEvent> live() {
        return query(EVENT + "WHERE state IN (?, ?) ORDER BY starts, id")
                .single(call().bind(EventState.SCHEDULED.name()).bind(EventState.ACTIVE.name()))
                .map(this::parse)
                .all();
    }

    @Override
    public Optional<ServerEvent> latest(String template) {
        return query(EVENT
                        + "WHERE template = ? AND (closed_by IS NULL OR closed_by <> ?) ORDER BY starts DESC, id DESC"
                        + " LIMIT 1")
                .single(call().bind(template).bind(ServerEvent.TEMPLATE_BY))
                .map(this::parse)
                .first();
    }

    @Override
    public Optional<ServerEvent> createdBy(String createdBy) {
        return query(EVENT + "WHERE created_by = ? ORDER BY id LIMIT 1")
                .single(call().bind(createdBy))
                .map(this::parse)
                .first();
    }

    @Override
    public boolean notice(int event, UUID player, LocalDateTime at) {
        @Language("mariadb")
        var insert = """
                INSERT INTO server_event_notices(event_id, player_uuid, noticed)
                VALUES (?, ?, ?)""";
        var errors = new ArrayList<SQLException>();
        var conf = Transactions.open(errors);
        try (conf) {
            conf.query(insert)
                    .single(call().bind(event).bind(player, UUID_BYTES).bind(at, time()))
                    .insert();
            if (!errors.isEmpty()) {
                Transactions.rollback(conf, errors);
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean state(int event, EventState from, EventState to, @Nullable String by, LocalDateTime at) {
        @Language("mariadb")
        var update = """
                UPDATE server_events
                SET state     = ?,
                    closed    = ?,
                    closed_by = ?
                WHERE id = ?
                  AND state = ?""";
        return query(update)
                .single(call().bind(to.name())
                        .bind(to.closed() ? at : null, time())
                        .bind(to.closed() ? by : null)
                        .bind(event)
                        .bind(from.name()))
                .update()
                .changed();
    }

    @Override
    public boolean expire(int event, EventState from, LocalDateTime ends, @Nullable String by, LocalDateTime at) {
        @Language("mariadb")
        var update = """
                UPDATE server_events
                SET state     = ?,
                    closed    = ?,
                    closed_by = ?
                WHERE id = ?
                  AND state = ?
                  AND ends = ?""";
        return query(update)
                .single(call().bind(EventState.ENDED.name())
                        .bind(at, time())
                        .bind(by)
                        .bind(event)
                        .bind(from.name())
                        .bind(ends, time()))
                .update()
                .changed();
    }

    @Override
    public boolean ends(int event, LocalDateTime from, LocalDateTime to) {
        @Language("mariadb")
        var update = """
                UPDATE server_events
                SET ends = ?
                WHERE id = ?
                  AND ends = ?
                  AND state IN (?, ?)
                  AND starts < ?""";
        return query(update)
                .single(call().bind(to, time())
                        .bind(event)
                        .bind(from, time())
                        .bind(EventState.SCHEDULED.name())
                        .bind(EventState.ACTIVE.name())
                        .bind(to, time()))
                .update()
                .changed();
    }

    @Override
    public boolean pay(Map<Integer, Double> spend, List<EventPayout> payouts) {
        @Language("mariadb")
        var take = """
                UPDATE server_events
                SET spent = spent + ?
                WHERE id = ?
                  AND state = ?
                  AND (budget IS NULL OR spent + ? <= budget + ?)""";
        @Language("mariadb")
        var record = """
                INSERT INTO order_payouts(order_id, company_id, worker_uuid, items, gross, tax, share, net, paid,
                                          created, event_extra)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id""";
        @Language("mariadb")
        var share = """
                INSERT INTO payout_events(payout_id, event_id, amount)
                VALUES (?, ?, ?)""";
        var errors = new ArrayList<SQLException>();
        var conf = Transactions.open(errors);
        try (conf) {
            for (var entry : new TreeMap<>(spend).entrySet()) {
                if (!(entry.getValue() > 0)) continue;
                var taken = conf.query(take)
                        .single(call().bind(entry.getValue())
                                .bind(entry.getKey())
                                .bind(EventState.ACTIVE.name())
                                .bind(entry.getValue())
                                .bind(BUDGET_TOLERANCE))
                        .update()
                        .changed();
                if (!taken || !errors.isEmpty()) {
                    Transactions.rollback(conf, errors);
                    return false;
                }
            }
            for (var payout : payouts) {
                var row = payout.record();
                var id = conf.query(record)
                        .single(call().bind(row.order())
                                .bind(row.solo() ? null : (Integer) row.company())
                                .bind(row.worker(), UUID_BYTES)
                                .bind(row.items())
                                .bind(row.gross())
                                .bind(row.tax())
                                .bind(row.share())
                                .bind(row.net())
                                .bind(row.paid())
                                .bind(row.created(), time())
                                .bind(row.eventExtra()))
                        .map(result -> result.getLong(1))
                        .first();
                if (id.isEmpty() || !errors.isEmpty()) {
                    Transactions.rollback(conf, errors);
                    return false;
                }
                for (var part : payout.byEvent().entrySet()) {
                    conf.query(share)
                            .single(call().bind(id.get()).bind(part.getKey()).bind(part.getValue()))
                            .insert();
                }
                if (!errors.isEmpty()) {
                    Transactions.rollback(conf, errors);
                    return false;
                }
            }
        }
        return errors.isEmpty();
    }

    @Override
    public boolean budget(int event, @Nullable Double budget) {
        if (budget == null) {
            @Language("mariadb")
            var remove = """
                    UPDATE server_events
                    SET budget = NULL
                    WHERE id = ?
                      AND kind = ?
                      AND funding = ?
                      AND state IN (?, ?)""";
            return query(remove)
                    .single(call().bind(event)
                            .bind(EventKind.REWARD_MULTIPLIER.name())
                            .bind(MoneySource.SourceType.ACCOUNT.name())
                            .bind(EventState.SCHEDULED.name())
                            .bind(EventState.ACTIVE.name()))
                    .update()
                    .changed();
        }
        @Language("mariadb")
        var update = """
                UPDATE server_events
                SET budget = ?
                WHERE id = ?
                  AND kind = ?
                  AND state IN (?, ?)
                  AND spent <= ?""";
        return query(update)
                .single(call().bind(budget)
                        .bind(event)
                        .bind(EventKind.REWARD_MULTIPLIER.name())
                        .bind(EventState.SCHEDULED.name())
                        .bind(EventState.ACTIVE.name())
                        .bind(budget))
                .update()
                .changed();
    }

    private ServerEvent parse(Row row) throws SQLException {
        var funding = row.getString("funding");
        return new ServerEvent(
                row.getInt("id"),
                row.getString("name"),
                EventKind.valueOf(row.getString("kind")),
                row.getDouble("value"),
                split(row.getString("scope_materials")),
                split(row.getString("scope_levels")).stream()
                        .map(Integer::parseInt)
                        .toList(),
                readTime(row, "starts"),
                readTime(row, "ends"),
                funding == null ? null : MoneySource.SourceType.valueOf(funding),
                row.getString("account"),
                row.getObject("budget") == null ? null : row.getDouble("budget"),
                row.getDouble("spent"),
                EventState.valueOf(row.getString("state")),
                row.getString("template"),
                row.getString("created_by"),
                readTime(row, "created"),
                row.getObject("closed") == null ? null : readTime(row, "closed"),
                row.getString("closed_by"));
    }

    private static @Nullable String join(List<?> values) {
        if (values.isEmpty()) return null;
        return values.stream().map(String::valueOf).collect(Collectors.joining(ServerEvent.SEPARATOR));
    }

    private static List<String> split(@Nullable String values) {
        if (values == null || values.isBlank()) return List.of();
        return Arrays.stream(values.split(ServerEvent.SEPARATOR))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
