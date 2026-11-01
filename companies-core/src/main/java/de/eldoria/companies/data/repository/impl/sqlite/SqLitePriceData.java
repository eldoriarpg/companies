/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.repository.impl.postgres.PostgresPriceData;
import org.intellij.lang.annotations.Language;

import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Shop prices for SQLite. Times are stored as text in the format of {@code CURRENT_TIMESTAMP}, so they compare
 * correctly as strings, and the lower and higher of two values are taken with {@code MIN} and {@code MAX}.
 */
public class SqLitePriceData extends PostgresPriceData {
    @Override
    protected Adapter<LocalDateTime> time() {
        return TimeColumns.SQLITE_TEXT;
    }

    @Override
    protected LocalDateTime readTime(Row row, String column) throws SQLException {
        return SqLiteAdapter.getTimestamp(row, column);
    }

    @Override
    @Language("sqlite")
    protected String addTrade() {
        return """
                INSERT INTO shop_trade_day(day, source, material, side, origin, trades, items, total, min_unit, max_unit)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (day, source, material, side, origin)
                    DO UPDATE SET trades   = trades + excluded.trades,
                                  items    = items + excluded.items,
                                  total    = total + excluded.total,
                                  min_unit = MIN(min_unit, excluded.min_unit),
                                  max_unit = MAX(max_unit, excluded.max_unit)""";
    }
}
