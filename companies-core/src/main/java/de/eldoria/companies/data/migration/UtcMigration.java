/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.migration;

import de.eldoria.companies.data.repository.impl.sqlite.SqLiteAdapter;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Converts the stored times to UTC once, when the schema moves to 1.14. Before, the plugin wrote the machine's local
 * time, and the databases wrote their own: SQLite UTC, PostgreSQL the session zone, which its driver sets to the
 * machine's, and MariaDB into {@code TIMESTAMP} columns, which hold an instant and read as UTC with the UTC session
 * the plugin now opens.
 * <p>
 * So every column the plugin wrote is converted from the machine's zone, and the columns the database wrote only on
 * PostgreSQL. A column the plugin filled with a time the database wrote, the order history's {@code placed}, follows
 * the database: left alone on SQLite, from the machine's zone on PostgreSQL, and on MariaDB from the zone of the old
 * session, the database's own, which MariaDB applies itself. The dashboard tables were always UTC.
 * <p>
 * The run first notes the machine's zone in {@code companies_utc}; a second run fails on that row before it changes
 * anything, so the times are converted exactly once. Rows are converted one by one by their key, aware of summer
 * time; a wall time in the hour summer time skips is moved forward, one that occurs twice takes the earlier instant.
 */
public final class UtcMigration {
    private static final List<Columns> COLUMNS = List.of(
            plugin("company_invites", "id", "created", "expires"),
            plugin("company_applications", "id", "created", "expires"),
            plugin("company_inbox", "id", "created", "read_at"),
            plugin("order_payouts", "id", "created"),
            plugin("order_history", "id", "finished"),
            copy("order_history", "id", "placed"),
            plugin("treasury_log", "id", "created"),
            plugin("order_series", "id", "ends", "next_due", "created", "updated"),
            plugin("order_alerts", "id", "created", "last_notified"),
            plugin("node", "id", "last_seen"),
            plugin("solo_cooldowns", "worker_uuid", "ends_at"),
            plugin("node_messages", "id", "created"),
            database("companies", "id", "founded"),
            database("orders", "id", "created"),
            database("order_states", "id", "last_update", "purge_warning"),
            database("item_returns", "id", "created"),
            database("order_deliveries", "id", "created"));

    private final Dialect dialect;
    private final ZoneId machine;
    private final String prefix;

    /**
     * @param dialect the database
     * @param machine the zone the plugin wrote its times in
     * @param schema  the schema of the tables on PostgreSQL, ignored elsewhere
     */
    public UtcMigration(Dialect dialect, ZoneId machine, String schema) {
        this.dialect = dialect;
        this.machine = machine;
        prefix = dialect == Dialect.POSTGRES ? schema + "." : "";
    }

    /**
     * Converts every column that needs it.
     *
     * @param connection the connection of the schema update
     * @return how many rows were converted
     * @throws SQLException when a table cannot be read or written
     */
    public int run(Connection connection) throws SQLException {
        try (var marker = connection.prepareStatement(
                "INSERT INTO %scompanies_utc(id, converted_from) VALUES (1, ?)".formatted(prefix))) {
            marker.setString(1, machine.getId());
            marker.executeUpdate();
        }
        var converted = 0;
        for (var columns : COLUMNS) {
            if (columns.writer() == Writer.PLUGIN || dialect == Dialect.POSTGRES) {
                converted += convert(connection, columns);
            } else if (columns.writer() == Writer.COPY && dialect == Dialect.MARIADB) {
                converted += convertInDatabaseZone(connection, columns);
            }
        }
        return converted;
    }

    private int convertInDatabaseZone(Connection connection, Columns columns) throws SQLException {
        var seconds = String.join(
                ", ",
                columns.columns().stream()
                        .map(column -> "UNIX_TIMESTAMP(" + column + ")")
                        .toList());
        List<Object[]> updates = new ArrayList<>();
        try (var statement = connection.createStatement()) {
            statement.execute("SET time_zone = @@global.time_zone");
            try (var rows = statement.executeQuery(
                    "SELECT %s, %s FROM %s".formatted(columns.key(), seconds, prefix + columns.table()))) {
                while (rows.next()) {
                    var values = new Object[columns.columns().size() + 1];
                    for (var index = 0; index < columns.columns().size(); index++) {
                        var second = rows.getObject(index + 2);
                        values[index] = second == null
                                ? null
                                : LocalDateTime.ofEpochSecond(((Number) second).longValue(), 0, ZoneOffset.UTC);
                    }
                    values[values.length - 1] = rows.getObject(1);
                    updates.add(values);
                }
            } finally {
                statement.execute("SET time_zone = '+00:00'");
            }
        }
        return update(connection, columns, updates);
    }

    private int convert(Connection connection, Columns columns) throws SQLException {
        var table = prefix + columns.table();
        var names = String.join(", ", columns.columns());
        List<Object[]> updates = new ArrayList<>();
        try (var select = connection.prepareStatement("SELECT %s, %s FROM %s".formatted(columns.key(), names, table));
                var rows = select.executeQuery()) {
            while (rows.next()) updates.add(row(rows, columns));
        }
        return update(connection, columns, updates);
    }

    private int update(Connection connection, Columns columns, List<Object[]> updates) throws SQLException {
        var table = prefix + columns.table();
        var sets = String.join(
                ", ", columns.columns().stream().map(column -> column + " = ?").toList());
        try (var update =
                connection.prepareStatement("UPDATE %s SET %s WHERE %s = ?".formatted(table, sets, columns.key()))) {
            for (var values : updates) {
                for (var index = 0; index < values.length; index++) update.setObject(index + 1, values[index]);
                update.addBatch();
            }
            update.executeBatch();
        }
        return updates.size();
    }

    private Object[] row(ResultSet rows, Columns columns) throws SQLException {
        var values = new Object[columns.columns().size() + 1];
        for (var index = 0; index < columns.columns().size(); index++) {
            values[index] = write(convert(read(rows, columns.columns().get(index))));
        }
        values[values.length - 1] = rows.getObject(columns.key());
        return values;
    }

    private LocalDateTime read(ResultSet rows, String column) throws SQLException {
        if (dialect != Dialect.SQLITE) return rows.getObject(column, LocalDateTime.class);
        var text = rows.getString(column);
        return text == null ? null : LocalDateTime.parse(text.replace(' ', 'T'), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private LocalDateTime convert(LocalDateTime local) {
        if (local == null) return null;
        return local.atZone(machine).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private Object write(LocalDateTime utc) {
        if (utc == null || dialect != Dialect.SQLITE) return utc;
        return SqLiteAdapter.format(utc);
    }

    private static Columns plugin(String table, String key, String... columns) {
        return new Columns(table, key, Writer.PLUGIN, List.of(columns));
    }

    private static Columns database(String table, String key, String... columns) {
        return new Columns(table, key, Writer.DATABASE, List.of(columns));
    }

    private static Columns copy(String table, String key, String... columns) {
        return new Columns(table, key, Writer.COPY, List.of(columns));
    }

    /**
     * The databases the plugin stores in.
     */
    public enum Dialect {
        /**
         * SQLite, which stores times as text and wrote its own as UTC.
         */
        SQLITE,
        /**
         * MariaDB, whose database-written columns hold instants.
         */
        MARIADB,
        /**
         * PostgreSQL, which wrote its own times in the machine's zone.
         */
        POSTGRES;

        /**
         * @param name the storage type's name
         * @return the dialect
         */
        public static Dialect of(String name) {
            return valueOf(name.toUpperCase(Locale.ROOT));
        }
    }

    /**
     * Who wrote a column.
     */
    private enum Writer {
        PLUGIN,
        DATABASE,
        COPY
    }

    /**
     * The time columns of a table and the key its rows are found by.
     *
     * @param table   the table
     * @param key     the column naming a row
     * @param writer  who wrote the times
     * @param columns the time columns
     */
    private record Columns(String table, String key, Writer writer, List<String> columns) {}
}
