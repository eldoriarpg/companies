/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import java.sql.SQLException;

/**
 * Recognises errors caused by a unique, primary key or foreign key constraint in all supported databases.
 * <p>
 * MariaDB and PostgreSQL report the SQL state class {@code 23}. SQLite reports no SQL state and the result code
 * {@code SQLITE_CONSTRAINT} (19) in the lowest byte of its extended error code.
 */
public final class ConstraintViolations {
    private static final int SQLITE_CONSTRAINT = 19;

    private ConstraintViolations() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Whether the exception was caused by a violated constraint.
     *
     * @param exception exception reported by the driver
     * @return true for constraint violations
     */
    public static boolean isViolation(SQLException exception) {
        var state = exception.getSQLState();
        if (state != null) return state.startsWith("23");
        return (exception.getErrorCode() & 0xFF) == SQLITE_CONSTRAINT;
    }
}
