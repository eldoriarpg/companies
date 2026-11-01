/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.postgres;

import de.eldoria.companies.data.repository.impl.mariadb.MariaDbPriceData;
import org.intellij.lang.annotations.Language;

/**
 * Shop prices for PostgreSQL, which runs the statements of MariaDB apart from adding trades to a day.
 */
public class PostgresPriceData extends MariaDbPriceData {
    @Override
    @Language("postgresql")
    protected String addTrade() {
        return """
                INSERT INTO shop_trade_day AS t(day, source, material, side, origin, trades, items, total, min_unit,
                                               max_unit)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (day, source, material, side, origin)
                    DO UPDATE SET trades   = t.trades + excluded.trades,
                                  items    = t.items + excluded.items,
                                  total    = t.total + excluded.total,
                                  min_unit = LEAST(t.min_unit, excluded.min_unit),
                                  max_unit = GREATEST(t.max_unit, excluded.max_unit)""";
    }
}
