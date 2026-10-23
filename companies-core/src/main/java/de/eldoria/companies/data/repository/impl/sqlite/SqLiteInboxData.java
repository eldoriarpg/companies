/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.repository.impl.postgres.PostgresInboxData;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;

/**
 * The inbox for SQLite. Times are stored as text in the format of {@code CURRENT_TIMESTAMP}, so they compare correctly
 * as strings, and the arguments are plain text.
 */
public class SqLiteInboxData extends PostgresInboxData {
    public SqLiteInboxData(ExecutorService executor) {
        super(executor);
    }

    @Override
    protected Adapter<LocalDateTime> time() {
        return TimeColumns.SQLITE_TEXT;
    }

    @Override
    protected LocalDateTime readTime(Row row, String column) throws SQLException {
        return SqLiteAdapter.getTimestamp(row, column);
    }

    @Override
    protected @Nullable LocalDateTime readNullableTime(Row row, String column) throws SQLException {
        return row.getString(column) == null ? null : SqLiteAdapter.getTimestamp(row, column);
    }

    @Override
    protected String jsonParameter() {
        return "?";
    }
}
