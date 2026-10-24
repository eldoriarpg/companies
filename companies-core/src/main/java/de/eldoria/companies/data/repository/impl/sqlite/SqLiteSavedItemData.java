/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.repository.impl.postgres.PostgresSavedItemData;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;

/**
 * The saved items for SQLite. Times are stored as text in the format of {@code CURRENT_TIMESTAMP}.
 */
public class SqLiteSavedItemData extends PostgresSavedItemData {
    public SqLiteSavedItemData(ObjectMapper mapper, ExecutorService executor) {
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
}
