/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.components.ranking.TopOrder;
import de.eldoria.companies.data.repository.AStatsData;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.wrapper.company.CompanyRank;
import de.eldoria.companies.data.wrapper.stats.MemberStats;
import de.eldoria.companies.data.wrapper.stats.PayoutRecord;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * Payout ledger, ranking and member statistics for MariaDB. The statements work in every dialect; the others only
 * change how times are bound and read.
 * <p>
 * A ranking is built in three layers: the source yields one row per company with uniform columns, the ranking numbers
 * the rows by the ordering, and the outer select pages or filters the numbered rows. The ordering is a fixed
 * expression per {@link TopOrder}, never user input.
 */
public class MariaDbStatsData extends AStatsData {
    @Language("mariadb")
    private static final String ALL_TIME_SOURCE = """
            SELECT id,
                   name,
                   founded,
                   COALESCE(member_count, 0) AS member_count,
                   COALESCE(order_count, 0)  AS order_count,
                   COALESCE(price, 0)        AS price,
                   COALESCE(amount, 0)       AS amount,
                   level,
                   tag,
                   color
            FROM company_stats_view""";

    @Language("mariadb")
    private static final String WINDOW_SOURCE = """
            SELECT c.id,
                   c.name,
                   c.founded,
                   COALESCE(m.member_count, 0) AS member_count,
                   a.order_count,
                   a.price,
                   a.amount,
                   c.level,
                   c.tag,
                   c.color
            FROM (SELECT company_id,
                         COUNT(DISTINCT order_id) AS order_count,
                         SUM(gross)               AS price,
                         SUM(items)               AS amount
                  FROM order_payouts
                  WHERE created >= ?
                  GROUP BY company_id) a
                     JOIN companies c
                          ON c.id = a.company_id
                     LEFT JOIN (SELECT id, COUNT(1) AS member_count
                                FROM company_member
                                GROUP BY id) m
                               ON m.id = c.id""";

    public MariaDbStatsData(ExecutorService executor) {
        super(executor);
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

    @Override
    protected boolean savePayouts(List<PayoutRecord> payouts) {
        @Language("mariadb")
        var query = """
                INSERT INTO order_payouts(order_id, company_id, worker_uuid, items, gross, tax, share, net, paid, created)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
        var result = query(query)
                .batch(payouts.stream().map(payout -> call().bind(payout.order())
                        .bind(payout.solo() ? null : (Integer) payout.company())
                        .bind(payout.worker(), UUID_BYTES)
                        .bind(payout.items())
                        .bind(payout.gross())
                        .bind(payout.tax())
                        .bind(payout.share())
                        .bind(payout.net())
                        .bind(payout.paid())
                        .bind(payout.created(), time())))
                .insert();
        return !result.hasExceptions() && result.rows() == payouts.size();
    }

    @Override
    protected List<MemberStats> getMemberStats(int company, @Nullable LocalDateTime since) {
        @Language("mariadb")
        var query = """
                SELECT worker_uuid,
                       SUM(items)                               AS items,
                       SUM(CASE WHEN paid THEN net ELSE 0 END)  AS earned,
                       COUNT(DISTINCT order_id)                 AS orders
                FROM order_payouts
                WHERE company_id = ?
                  %s
                GROUP BY worker_uuid
                ORDER BY earned DESC, items DESC""";
        var call = since == null ? call().bind(company) : call().bind(company).bind(since, time());
        return query(query, since == null ? "" : "AND created >= ?")
                .single(call)
                .map(row -> new MemberStats(
                        row.get("worker_uuid", UUID_BYTES),
                        row.getInt("items"),
                        row.getDouble("earned"),
                        row.getInt("orders")))
                .all();
    }

    @Override
    protected List<CompanyRank> getRanking(TopOrder order, TimeRange range, LocalDateTime now, int page, int size) {
        var call = sourceCall(range, now).bind(size).bind((page - 1) * size);
        return query(ranked(order, range) + " ORDER BY comp_rank LIMIT ? OFFSET ?")
                .single(call)
                .map(this::parseRank)
                .all();
    }

    @Override
    protected Optional<CompanyRank> getRank(int company, TopOrder order, TimeRange range, LocalDateTime now) {
        var call = sourceCall(range, now).bind(company);
        return query(ranked(order, range) + " WHERE id = ?")
                .single(call)
                .map(this::parseRank)
                .first();
    }

    @Override
    protected Map<Integer, Integer> getRankIndex(TopOrder order) {
        Map<Integer, Integer> index = new HashMap<>();
        query(ranked(order, TimeRange.ALL))
                .single()
                .map(row -> Map.entry(row.getInt("id"), row.getInt("comp_rank")))
                .all()
                .forEach(entry -> index.put(entry.getKey(), entry.getValue()));
        return index;
    }

    /**
     * The numbered rows of a ranking, as a select whose rows can be paged or filtered by appending a clause.
     *
     * @param order ordering
     * @param range time range
     * @return select of the numbered rows
     */
    private static String ranked(TopOrder order, TimeRange range) {
        var source = range == TimeRange.ALL ? ALL_TIME_SOURCE : WINDOW_SOURCE;
        return """
                SELECT r.*
                FROM (SELECT ROW_NUMBER() OVER (ORDER BY %s, s.id) AS comp_rank, s.*
                      FROM (%s) s) r""".formatted(ordering(order), source);
    }

    /**
     * The sort expression of an ordering over the uniform columns of the ranking sources.
     *
     * @param order ordering
     * @return sort expression
     */
    private static String ordering(TopOrder order) {
        return switch (order) {
            case ORDERS -> "s.order_count DESC";
            case EARNED -> "s.price DESC";
            case MEMBER -> "s.member_count DESC";
            case ITEMS_DELIVERED -> "s.amount DESC";
            case LEVEL -> "s.level DESC, s.price DESC";
        };
    }

    private Call sourceCall(TimeRange range, LocalDateTime now) {
        var start = range.start(now);
        return start.isPresent() ? call().bind(start.get(), time()) : call();
    }

    private CompanyRank parseRank(Row row) throws SQLException {
        return new CompanyRank(
                row.getInt("comp_rank"),
                row.getInt("id"),
                row.getString("name"),
                readTime(row, "founded"),
                row.getInt("member_count"),
                row.getInt("order_count"),
                row.getDouble("price"),
                row.getInt("amount"),
                row.getInt("level"),
                row.getString("tag"),
                row.getString("color"));
    }
}
