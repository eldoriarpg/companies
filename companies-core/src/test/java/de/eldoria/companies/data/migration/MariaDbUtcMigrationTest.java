/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.migration;

import de.chojo.sadu.mariadb.databases.MariaDb;
import de.chojo.sadu.updater.BaseSqlUpdaterBuilder;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import org.junit.jupiter.api.AfterEach;
import org.mariadb.jdbc.MariaDbDataSource;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.SQLException;
import java.time.LocalDateTime;

import javax.sql.DataSource;

class MariaDbUtcMigrationTest extends UtcMigrationContract {
    private MariaDBContainer mariadb;

    @Override
    protected DataSource start() throws Exception {
        mariadb = new MariaDBContainer(DockerImageName.parse("mariadb:11.4"));
        mariadb.start();
        var dataSource = new MariaDbDataSource(
                mariadb.getJdbcUrl() + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        dataSource.setUser(mariadb.getUsername());
        dataSource.setPassword(mariadb.getPassword());
        return dataSource;
    }

    @AfterEach
    void stop() {
        if (mariadb != null) mariadb.stop();
    }

    @Override
    protected BaseSqlUpdaterBuilder<?, ?> updater(DataSource dataSource) {
        return SqlUpdater.builder(dataSource, MariaDb.get())
                .setVersionTable("companies_db_version")
                .withClassLoader(getClass().getClassLoader())
                .setReplacements(new QueryReplacement("companies_schema", mariadb.getDatabaseName()));
    }

    @Override
    protected UtcMigration.Dialect dialect() {
        return UtcMigration.Dialect.MARIADB;
    }

    @Override
    protected String schema() {
        return mariadb.getDatabaseName();
    }

    @Override
    protected String literal(LocalDateTime time) {
        return "TIMESTAMP '" + time.toString().replace('T', ' ') + ":00'";
    }

    @Override
    protected LocalDateTime time(DataSource dataSource, String query) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery(query)) {
            return rows.next() ? rows.getObject(1, LocalDateTime.class) : null;
        }
    }

    @Override
    protected boolean databaseTimesConverted() {
        return false;
    }
}
