/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.chojo.sadu.queries.api.configuration.ConnectedQueryConfiguration;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.repository.impl.postgres.PostgresAlertData;
import org.intellij.lang.annotations.Language;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import static de.chojo.sadu.queries.api.call.Call.call;

/**
 * Order alerts for SQLite. Times are stored as text in the format of {@code CURRENT_TIMESTAMP}, so they compare
 * correctly as strings. SQLite has no row locks; a no-op update takes the write lock of the database instead.
 */
public class SqLiteAlertData extends PostgresAlertData {
    public SqLiteAlertData(ObjectMapper mapper, ExecutorService executor) {
        super(mapper, executor);
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
    protected Optional<LocalDateTime> readNullableTime(Row row, String column) throws SQLException {
        return row.getString(column) == null ? Optional.empty() : Optional.of(SqLiteAdapter.getTimestamp(row, column));
    }

    @Override
    protected boolean lockCompany(ConnectedQueryConfiguration conf, int company) {
        @Language("sqlite")
        var query = """
                UPDATE companies SET join_mode = join_mode WHERE id = ?""";
        return conf.query(query).single(call().bind(company)).update().changed();
    }
}
