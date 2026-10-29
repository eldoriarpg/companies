/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.repository.impl.postgres.PostgresOfferData;
import org.jetbrains.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Event offers for SQLite. Times are stored as text in the format of {@code CURRENT_TIMESTAMP}, so they compare
 * correctly as strings.
 */
public class SqLiteOfferData extends PostgresOfferData {
    /**
     * @param mapper mapper for the exact items of offers, able to read and write item stacks
     */
    public SqLiteOfferData(ObjectMapper mapper) {
        super(mapper);
    }

    @Override
    protected Adapter<LocalDateTime> time() {
        return TimeColumns.SQLITE_TEXT;
    }

    @Override
    protected @Nullable LocalDateTime readTime(Row row, String column) throws SQLException {
        return row.getString(column) == null ? null : SqLiteAdapter.getTimestamp(row, column);
    }
}
