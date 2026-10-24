/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.migration;

import de.chojo.sadu.postgresql.databases.PostgreSql;
import de.chojo.sadu.updater.BaseSqlUpdaterBuilder;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import org.junit.jupiter.api.AfterEach;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.SQLException;
import java.time.LocalDateTime;

import javax.sql.DataSource;

class PostgresUtcMigrationTest extends UtcMigrationContract {
    private PostgreSQLContainer postgres;

    @Override
    protected DataSource start() {
        postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
        postgres.start();
        var dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource;
    }

    @AfterEach
    void stop() {
        if (postgres != null) postgres.stop();
    }

    @Override
    protected BaseSqlUpdaterBuilder<?, ?> updater(DataSource dataSource) {
        return SqlUpdater.builder(dataSource, PostgreSql.get())
                .setVersionTable("companies_db_version")
                .withClassLoader(getClass().getClassLoader())
                .setReplacements(new QueryReplacement("companies_schema", "public"));
    }

    @Override
    protected UtcMigration.Dialect dialect() {
        return UtcMigration.Dialect.POSTGRES;
    }

    @Override
    protected String schema() {
        return "public";
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
        return true;
    }

    @Override
    protected boolean copiesConverted() {
        return true;
    }
}
