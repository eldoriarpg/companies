/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.eldoria.companies.components.price.ShopOrigin;
import de.eldoria.companies.components.price.ShopSide;
import de.eldoria.companies.data.repository.APriceData;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.repository.impl.Transactions;
import de.eldoria.companies.data.wrapper.price.Baseline;
import de.eldoria.companies.data.wrapper.price.QuoteGroup;
import de.eldoria.companies.data.wrapper.price.TradeDay;
import org.intellij.lang.annotations.Language;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * Shop prices for MariaDB. PostgreSQL and SQLite run the same statements, apart from adding trades to a day.
 */
public class MariaDbPriceData extends APriceData {
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
     * The statement that adds one total to the trades of its day, inserting the day when it has none yet. It takes the
     * day, source, material, side, origin, trades, items, total, lowest and highest unit price.
     *
     * @return the statement
     */
    @Language("mariadb")
    protected String addTrade() {
        return """
                INSERT INTO shop_trade_day(day, source, material, side, origin, trades, items, total, min_unit, max_unit)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE trades   = trades + VALUES(trades),
                                        items    = items + VALUES(items),
                                        total    = total + VALUES(total),
                                        min_unit = LEAST(min_unit, VALUES(min_unit)),
                                        max_unit = GREATEST(max_unit, VALUES(max_unit))""";
    }

    @Override
    public boolean addTrades(List<TradeDay> trades) {
        if (trades.isEmpty()) return true;
        var errors = new ArrayList<SQLException>();
        var conf = Transactions.open(errors);
        try (conf) {
            conf.query(addTrade())
                    .batch(trades.stream().map(trade -> call().bind(trade.day().toEpochDay())
                            .bind(trade.source())
                            .bind(trade.material())
                            .bind(trade.side().name())
                            .bind(trade.origin().name())
                            .bind(trade.trades())
                            .bind(trade.items())
                            .bind(trade.total())
                            .bind(trade.minUnit())
                            .bind(trade.maxUnit())))
                    .insert();
            if (!errors.isEmpty()) {
                Transactions.rollback(conf, errors);
                return false;
            }
        }
        return true;
    }

    @Override
    public List<TradeDay> trades(LocalDate from) {
        @Language("mariadb")
        var select = """
                SELECT day, source, material, side, origin, trades, items, total, min_unit, max_unit
                FROM shop_trade_day
                WHERE day >= ?""";
        return query(select)
                .single(call().bind(from.toEpochDay()))
                .map(row -> new TradeDay(
                        LocalDate.ofEpochDay(row.getLong("day")),
                        row.getString("source"),
                        row.getString("material"),
                        ShopSide.valueOf(row.getString("side")),
                        ShopOrigin.valueOf(row.getString("origin")),
                        row.getInt("trades"),
                        row.getLong("items"),
                        row.getDouble("total"),
                        row.getDouble("min_unit"),
                        row.getDouble("max_unit")))
                .all();
    }

    @Override
    public int purgeTrades(LocalDate before) {
        return query("DELETE FROM shop_trade_day WHERE day < ?")
                .single(call().bind(before.toEpochDay()))
                .delete()
                .rows();
    }

    @Override
    public boolean replaceQuotes(UUID node, List<QuoteGroup> quotes) {
        @Language("mariadb")
        var insert = """
                INSERT INTO shop_quote(source, node, material, side, origin, quotes, items, median_unit, min_unit,
                                       max_unit, sampled_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
        var errors = new ArrayList<SQLException>();
        var conf = Transactions.open(errors);
        try (conf) {
            conf.query("DELETE FROM shop_quote WHERE node = ?")
                    .single(call().bind(node, UUID_BYTES))
                    .delete();
            if (!quotes.isEmpty()) {
                conf.query(insert)
                        .batch(quotes.stream().map(quote -> call().bind(quote.source())
                                .bind(node, UUID_BYTES)
                                .bind(quote.material())
                                .bind(quote.side().name())
                                .bind(quote.origin().name())
                                .bind(quote.quotes())
                                .bind(quote.items())
                                .bind(quote.medianUnit())
                                .bind(quote.minUnit())
                                .bind(quote.maxUnit())
                                .bind(quote.sampledAt(), time())))
                        .insert();
            }
            if (!errors.isEmpty()) {
                Transactions.rollback(conf, errors);
                return false;
            }
        }
        return true;
    }

    @Override
    public List<QuoteGroup> quotes(LocalDateTime from) {
        @Language("mariadb")
        var select = """
                SELECT source, material, side, origin, quotes, items, median_unit, min_unit, max_unit, sampled_at
                FROM shop_quote
                WHERE sampled_at >= ?""";
        return query(select)
                .single(call().bind(from, time()))
                .map(row -> new QuoteGroup(
                        row.getString("source"),
                        row.getString("material"),
                        ShopSide.valueOf(row.getString("side")),
                        ShopOrigin.valueOf(row.getString("origin")),
                        row.getInt("quotes"),
                        row.getLong("items"),
                        row.getDouble("median_unit"),
                        row.getDouble("min_unit"),
                        row.getDouble("max_unit"),
                        readTime(row, "sampled_at")))
                .all();
    }

    @Override
    public boolean replaceBaselines(List<Baseline> baselines, LocalDateTime now) {
        @Language("mariadb")
        var insert = """
                INSERT INTO material_baseline(material, sell_unit, buy_unit, baseline_unit, basis, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)""";
        var errors = new ArrayList<SQLException>();
        var conf = Transactions.open(errors);
        try (conf) {
            conf.query("DELETE FROM material_baseline").single().delete();
            if (!baselines.isEmpty()) {
                conf.query(insert)
                        .batch(baselines.stream().map(baseline -> call().bind(baseline.material())
                                .bind(baseline.sellUnit())
                                .bind(baseline.buyUnit())
                                .bind(baseline.unit())
                                .bind(baseline.basis().name())
                                .bind(now, time())))
                        .insert();
            }
            if (!errors.isEmpty()) {
                Transactions.rollback(conf, errors);
                return false;
            }
        }
        return true;
    }
}
