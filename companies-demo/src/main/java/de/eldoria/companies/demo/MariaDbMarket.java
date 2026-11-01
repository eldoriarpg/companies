/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import org.mariadb.jdbc.MariaDbDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * The market tables of the Companies database on MariaDB, written over a connection of its own beside the running
 * plugin. Companies reads them fresh for the dashboard; its in-game price hints keep what they read for a few minutes
 * and show the written prices after that.
 */
final class MariaDbMarket implements MarketSeeder.Store {
    private final MariaDbDataSource source;

    /**
     * @param source the Companies database
     */
    MariaDbMarket(MariaDbDataSource source) {
        this.source = source;
    }

    /**
     * @param host     host of the database server
     * @param port     its port
     * @param database the Companies database
     * @param user     the user
     * @param password its password
     * @return the store
     * @throws SQLException when the address is invalid
     */
    static MariaDbMarket of(String host, int port, String database, String user, String password) throws SQLException {
        var source = new MariaDbDataSource("jdbc:mariadb://%s:%d/%s".formatted(host, port, database));
        source.setUser(user);
        source.setPassword(password);
        return new MariaDbMarket(source);
    }

    @Override
    public boolean hasHistory() {
        var found = new boolean[1];
        run(connection -> {
            try (var statement = connection.prepareStatement("SELECT 1 FROM market_history LIMIT 1");
                    var result = statement.executeQuery()) {
                found[0] = result.next();
            }
        });
        return found[0];
    }

    @Override
    public void days(List<MarketSeeder.Row> rows) {
        var insert = """
                INSERT INTO market_history(day, material, orders, items, avg_price, min_price, max_price)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE orders = VALUES(orders), items = VALUES(items), avg_price = VALUES(avg_price),
                                        min_price = VALUES(min_price), max_price = VALUES(max_price)""";
        run(connection -> {
            try (var statement = connection.prepareStatement(insert)) {
                for (var row : rows) {
                    statement.setLong(1, row.day().toEpochDay());
                    statement.setString(2, row.material());
                    statement.setInt(3, row.orders());
                    statement.setLong(4, row.items());
                    statement.setDouble(5, row.average());
                    statement.setDouble(6, row.lowest());
                    statement.setDouble(7, row.highest());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        });
    }

    @Override
    public void recordedThrough(LocalDate day) {
        run(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO market_history_state(id, recorded_through) VALUES (1, ?)
                    ON DUPLICATE KEY UPDATE recorded_through = GREATEST(recorded_through, VALUES(recorded_through))""")) {
                statement.setLong(1, day.toEpochDay());
                statement.executeUpdate();
            }
        });
    }

    @Override
    public void refreshPrices() {
        run(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO material_price(material, avg_price, min_price, max_price, orders)
                    SELECT material, ROUND(AVG(avg_price), 2), MIN(min_price), MAX(max_price), LEAST(SUM(orders), 99)
                    FROM market_history
                    GROUP BY material
                    ON DUPLICATE KEY UPDATE avg_price = VALUES(avg_price), min_price = VALUES(min_price),
                                            max_price = VALUES(max_price), orders = VALUES(orders)""")) {
                statement.executeUpdate();
            }
        });
    }

    private void run(Work work) {
        try (var connection = source.getConnection()) {
            work.run(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not write the market history", e);
        }
    }

    /**
     * Work on one connection.
     */
    @FunctionalInterface
    private interface Work {
        /**
         * @param connection the connection
         * @throws SQLException when the database refuses
         */
        void run(Connection connection) throws SQLException;
    }
}
