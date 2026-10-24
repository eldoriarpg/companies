/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.eldoria.companies.configuration.elements.NodeSettings;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.repository.impl.postgres.PostgresNodeData;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * The nodes for SQLite. Times are stored as text in the format of {@code CURRENT_TIMESTAMP}.
 */
public class SqLiteNodeData extends PostgresNodeData {
    public SqLiteNodeData(NodeSettings nodeSettings) {
        super(nodeSettings);
    }

    @Override
    protected Adapter<LocalDateTime> time() {
        return TimeColumns.SQLITE_TEXT;
    }

    @Override
    protected @Nullable LocalDateTime readLastSeen(Row row) throws SQLException {
        return row.getString("last_seen") == null ? null : SqLiteAdapter.getTimestamp(row, "last_seen");
    }
}
