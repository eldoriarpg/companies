/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.chojo.sadu.wrapper.util.Row;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class SqLiteAdapter {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private SqLiteAdapter() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    public static LocalDateTime getTimestamp(Row rs, String column) throws SQLException {
        return LocalDateTime.parse(rs.getString(column), FORMATTER);
    }

}
