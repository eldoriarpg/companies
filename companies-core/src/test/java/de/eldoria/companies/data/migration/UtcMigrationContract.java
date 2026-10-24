/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.migration;

import de.chojo.sadu.core.updater.SqlVersion;
import de.chojo.sadu.updater.BaseSqlUpdaterBuilder;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The move to schema 1.13 converts the stored times of a server in Berlin to UTC, as each dialect needs it.
 */
public abstract class UtcMigrationContract {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final SqlVersion BEFORE = new SqlVersion(1, 12);
    private static final SqlVersion AFTER = new SqlVersion(1, 14);

    /**
     * @return an empty database of the dialect
     */
    protected abstract DataSource start() throws Exception;

    /**
     * @param dataSource the database
     * @return the dialect's schema updater, with version table, replacements and class loader set
     */
    protected abstract BaseSqlUpdaterBuilder<?, ?> updater(DataSource dataSource);

    /**
     * @return the dialect
     */
    protected abstract UtcMigration.Dialect dialect();

    /**
     * @return the schema of the tables
     */
    protected abstract String schema();

    /**
     * Writes a stored time as the dialect stores it.
     *
     * @param time the wall time
     * @return the SQL literal
     */
    protected abstract String literal(LocalDateTime time);

    /**
     * Reads a stored time.
     *
     * @param dataSource the database
     * @param query      a query of one time
     * @return the time
     */
    protected abstract LocalDateTime time(DataSource dataSource, String query) throws SQLException;

    /**
     * @return whether the database wrote its own times in the machine's zone, so they are converted too
     */
    protected abstract boolean databaseTimesConverted();

    /**
     * @return whether copies of database times the plugin stored are converted, from the zone they were read in
     */
    protected abstract boolean copiesConverted();

    @Test
    void storedTimesBecomeUtcWithSummerTimeAndNullsKept() throws Exception {
        var dataSource = start();
        updater(dataSource).setVersion(BEFORE).execute();
        var summer = LocalDateTime.of(2026, 7, 1, 14, 0);
        var winter = LocalDateTime.of(2026, 1, 1, 14, 0);
        try (var connection = dataSource.getConnection();
                var node = connection.prepareStatement(
                        "INSERT INTO node(uid, version, last_seen) VALUES (?, '2.0.0', " + literal(summer) + ")");
                var empty = connection.prepareStatement("INSERT INTO node(uid, version) VALUES (?, '2.0.0')");
                var company = connection.createStatement()) {
            node.setBytes(1, bytes(UUID.randomUUID()));
            node.executeUpdate();
            empty.setBytes(1, bytes(UUID.randomUUID()));
            empty.executeUpdate();
            company.executeUpdate("INSERT INTO companies(name, founded) VALUES ('Miners', " + literal(winter) + ")");
            company.executeUpdate(
                    "INSERT INTO order_history(order_id, company_id, outcome, order_name, placed, finished, snapshot) "
                            + "VALUES (7, 1, 5, 'Walls', " + literal(summer) + ", " + literal(summer) + ", '{}')");
        }
        var migration = new UtcMigration(dialect(), BERLIN, schema());

        updater(dataSource)
                .setVersion(AFTER)
                .preUpdateHook(AFTER, migration::run)
                .execute();

        assertEquals(
                LocalDateTime.of(2026, 7, 1, 12, 0),
                time(dataSource, "SELECT last_seen FROM node WHERE last_seen IS NOT NULL"));
        assertNull(time(dataSource, "SELECT last_seen FROM node WHERE last_seen IS NULL"));
        assertEquals(
                databaseTimesConverted() ? LocalDateTime.of(2026, 1, 1, 13, 0) : winter,
                time(dataSource, "SELECT founded FROM companies"));
        assertEquals(LocalDateTime.of(2026, 7, 1, 12, 0), time(dataSource, "SELECT finished FROM order_history"));
        assertEquals(
                copiesConverted() ? LocalDateTime.of(2026, 7, 1, 12, 0) : summer,
                time(dataSource, "SELECT placed FROM order_history"));

        try (var connection = dataSource.getConnection()) {
            assertThrows(SQLException.class, () -> migration.run(connection));
        }
        assertEquals(
                LocalDateTime.of(2026, 7, 1, 12, 0),
                time(dataSource, "SELECT last_seen FROM node WHERE last_seen IS NOT NULL"));
    }

    private static byte[] bytes(UUID uuid) {
        var bytes = new byte[16];
        var most = uuid.getMostSignificantBits();
        var least = uuid.getLeastSignificantBits();
        for (var index = 0; index < 8; index++) {
            bytes[index] = (byte) (most >>> (56 - 8 * index));
            bytes[index + 8] = (byte) (least >>> (56 - 8 * index));
        }
        return bytes;
    }
}
