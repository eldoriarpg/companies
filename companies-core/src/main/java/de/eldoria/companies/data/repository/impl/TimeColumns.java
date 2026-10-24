/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl;

import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteAdapter;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;

/**
 * Binds points in time so that they compare correctly with the time columns of a dialect.
 */
public final class TimeColumns {
    /**
     * Binds a time as SQL timestamp, for MariaDB and PostgreSQL.
     */
    public static final Adapter<LocalDateTime> TIMESTAMP =
            Adapter.create(PreparedStatement::setTimestamp, Types.TIMESTAMP, Timestamp::valueOf);

    /**
     * Binds a time as text in the format of SQLite's {@code CURRENT_TIMESTAMP}, so it compares correctly as string.
     */
    public static final Adapter<LocalDateTime> SQLITE_TEXT =
            Adapter.create(PreparedStatement::setString, Types.VARCHAR, SqLiteAdapter::format);

    private TimeColumns() {
        throw new UnsupportedOperationException("This is a utility class.");
    }
}
