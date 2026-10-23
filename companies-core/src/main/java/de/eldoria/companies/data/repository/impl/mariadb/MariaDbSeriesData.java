/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.ASeriesData;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.repository.impl.Transactions;
import de.eldoria.companies.data.wrapper.series.OrderSeries;
import de.eldoria.companies.data.wrapper.series.RepeatInterval;
import de.eldoria.companies.data.wrapper.series.SeriesState;
import de.eldoria.companies.data.wrapper.series.SeriesStopReason;
import de.eldoria.companies.data.wrapper.series.SeriesWait;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * Repeating orders for MariaDB. The statements work in every dialect; the others only change how times are bound and
 * read.
 * <p>
 * A claim is conditional on the number of posted copies the caller read, so it succeeds once per copy even when two
 * servers process the same due series.
 */
public class MariaDbSeriesData extends ASeriesData {
    @Language("mariadb")
    private static final String SERIES = """
            SELECT id, owner_uuid, name, content, repeat_every, max_copies, ends, posted, next_due, current_order,
                   state, stop_reason, waiting, order_limit, created
            FROM order_series
            """;

    public MariaDbSeriesData(ObjectMapper mapper, ExecutorService executor) {
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

    @Override
    protected Optional<OrderSeries> saveSeries(OrderSeries draft) {
        @Language("mariadb")
        var query = """
                INSERT INTO order_series(owner_uuid, name, content, repeat_every, max_copies, ends, posted, next_due,
                                         current_order, state, stop_reason, waiting, order_limit, created, updated)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id""";
        return query(query)
                .single(call().bind(draft.owner(), UUID_BYTES)
                        .bind(draft.name())
                        .bind(toJson(draft.lines()))
                        .bind(draft.interval().id())
                        .bind(boxed(draft.maxCopies()))
                        .bind(draft.ends().orElse(null), time())
                        .bind(draft.posted())
                        .bind(draft.nextDue(), time())
                        .bind(boxed(draft.currentOrder()))
                        .bind(draft.state().id())
                        .bind(draft.stopReason().map(SeriesStopReason::id).orElse(null))
                        .bind(draft.waiting().map(SeriesWait::id).orElse(null))
                        .bind(draft.orderLimit())
                        .bind(draft.created(), time())
                        .bind(draft.created(), time()))
                .map(row -> row.getInt(1))
                .first()
                .map(draft::withId);
    }

    @Override
    protected Optional<OrderSeries> getSeries(int id) {
        return query(SERIES + "WHERE id = ?")
                .single(call().bind(id))
                .map(this::parseSeries)
                .first();
    }

    @Override
    protected List<OrderSeries> getByOwner(UUID owner, boolean activeOnly) {
        var filter = activeOnly ? "AND state = %d ".formatted(SeriesState.ACTIVE.id()) : "";
        return query(SERIES + "WHERE owner_uuid = ? " + filter + "ORDER BY created DESC, id DESC")
                .single(call().bind(owner, UUID_BYTES))
                .map(this::parseSeries)
                .all();
    }

    @Override
    protected int countActive(UUID owner) {
        @Language("mariadb")
        var query = """
                SELECT COUNT(1) AS count FROM order_series WHERE owner_uuid = ? AND state = ?""";
        return query(query)
                .single(call().bind(owner, UUID_BYTES).bind(SeriesState.ACTIVE.id()))
                .map(row -> row.getInt("count"))
                .first()
                .orElse(0);
    }

    @Override
    protected List<OrderSeries> getDue(LocalDateTime now, int limit) {
        return query(SERIES + "WHERE state = ? AND next_due <= ? ORDER BY next_due, id LIMIT ?")
                .single(call().bind(SeriesState.ACTIVE.id()).bind(now, time()).bind(limit))
                .map(this::parseSeries)
                .all();
    }

    @Override
    protected boolean claim(OrderSeries series, LocalDateTime nextDue, LocalDateTime now) {
        @Language("mariadb")
        var query = """
                UPDATE order_series
                SET next_due = ?,
                    posted   = posted + 1,
                    waiting  = NULL,
                    updated  = ?
                WHERE id = ?
                  AND state = ?
                  AND posted = ?""";
        return query(query)
                .single(call().bind(nextDue, time())
                        .bind(now, time())
                        .bind(series.id())
                        .bind(SeriesState.ACTIVE.id())
                        .bind(series.posted()))
                .update()
                .changed();
    }

    @Override
    protected boolean releaseClaim(OrderSeries series, LocalDateTime now) {
        @Language("mariadb")
        var query = """
                UPDATE order_series
                SET next_due = ?,
                    posted   = ?,
                    updated  = ?
                WHERE id = ?
                  AND posted = ?""";
        return query(query)
                .single(call().bind(series.nextDue(), time())
                        .bind(series.posted())
                        .bind(now, time())
                        .bind(series.id())
                        .bind(series.posted() + 1))
                .update()
                .changed();
    }

    @Override
    protected boolean saveCurrentOrder(int series, int order) {
        @Language("mariadb")
        var current = """
                UPDATE order_series SET current_order = ? WHERE id = ?""";
        @Language("mariadb")
        var copy = """
                UPDATE orders SET series_id = ? WHERE id = ?""";
        var errors = new ArrayList<SQLException>();
        var conf = Transactions.open(errors);
        try (conf) {
            var seriesFound = conf.query(current)
                    .single(call().bind(order).bind(series))
                    .update()
                    .changed();
            var orderFound = conf.query(copy)
                    .single(call().bind(series).bind(order))
                    .update()
                    .changed();
            if (!errors.isEmpty() || !seriesFound || !orderFound) {
                errors.add(new SQLException("Series " + series + " or order " + order + " does not exist"));
                Transactions.rollback(conf, errors);
                return false;
            }
            return true;
        }
    }

    @Override
    protected boolean markWaiting(int series, SeriesWait wait, LocalDateTime now) {
        @Language("mariadb")
        var query = """
                UPDATE order_series
                SET waiting = ?,
                    updated = ?
                WHERE id = ?
                  AND state = ?
                  AND (waiting IS NULL OR waiting <> ?)""";
        return query(query)
                .single(call().bind(wait.id())
                        .bind(now, time())
                        .bind(series)
                        .bind(SeriesState.ACTIVE.id())
                        .bind(wait.id()))
                .update()
                .changed();
    }

    @Override
    protected boolean stop(int series, SeriesState state, @Nullable SeriesStopReason reason, LocalDateTime now) {
        @Language("mariadb")
        var query = """
                UPDATE order_series
                SET state       = ?,
                    stop_reason = ?,
                    waiting     = NULL,
                    updated     = ?
                WHERE id = ?
                  AND state = ?""";
        return query(query)
                .single(call().bind(state.id())
                        .bind(reason == null ? (Integer) null : Integer.valueOf(reason.id()))
                        .bind(now, time())
                        .bind(series)
                        .bind(SeriesState.ACTIVE.id()))
                .update()
                .changed();
    }

    @Override
    protected boolean resume(int series, LocalDateTime nextDue, LocalDateTime now) {
        @Language("mariadb")
        var query = """
                UPDATE order_series
                SET state       = ?,
                    stop_reason = NULL,
                    waiting     = NULL,
                    next_due    = ?,
                    updated     = ?
                WHERE id = ?
                  AND state = ?""";
        return query(query)
                .single(call().bind(SeriesState.ACTIVE.id())
                        .bind(nextDue, time())
                        .bind(now, time())
                        .bind(series)
                        .bind(SeriesState.STOPPED.id()))
                .update()
                .changed();
    }

    @Override
    protected int saveOrderLimit(UUID owner, int limit) {
        @Language("mariadb")
        var query = """
                UPDATE order_series SET order_limit = ? WHERE owner_uuid = ?""";
        return query(query)
                .single(call().bind(limit).bind(owner, UUID_BYTES))
                .update()
                .rows();
    }

    @Override
    protected Optional<OrderState> getOrderState(int order) {
        @Language("mariadb")
        var query = """
                SELECT state FROM order_states WHERE id = ?""";
        return query(query)
                .single(call().bind(order))
                .map(row -> Optional.ofNullable(OrderState.byId(row.getInt("state"))))
                .first()
                .flatMap(state -> state);
    }

    private OrderSeries parseSeries(Row row) throws SQLException {
        var interval = row.getInt("repeat_every");
        var state = row.getInt("state");
        return new OrderSeries(
                row.getInt("id"),
                row.get("owner_uuid", UUID_BYTES),
                row.getString("name"),
                toLines(row.getString("content")),
                RepeatInterval.byId(interval)
                        .orElseThrow(() -> new SQLException("Unknown repeat interval " + interval)),
                nullableInt(row, "max_copies"),
                readNullableTime(row, "ends"),
                row.getInt("posted"),
                readTime(row, "next_due"),
                nullableInt(row, "current_order"),
                SeriesState.byId(state).orElseThrow(() -> new SQLException("Unknown series state " + state)),
                nullableInt(row, "stop_reason").stream().boxed().findFirst().flatMap(SeriesStopReason::byId),
                nullableInt(row, "waiting").stream().boxed().findFirst().flatMap(SeriesWait::byId),
                row.getInt("order_limit"),
                readTime(row, "created"));
    }

    private static OptionalInt nullableInt(Row row, String column) throws SQLException {
        return row.getObject(column) instanceof Number number ? OptionalInt.of(number.intValue()) : OptionalInt.empty();
    }

    private static @Nullable Integer boxed(OptionalInt value) {
        return value.isPresent() ? value.getAsInt() : null;
    }
}
