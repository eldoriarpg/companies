/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.chojo.sadu.postgresql.databases.PostgreSql;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.sql.SQLException;

import javax.sql.DataSource;

/**
 * Low latency mode on PostgreSQL.
 */
class PostgresLowLatencyIntegrationTest extends LowLatencyIntegrationTest {
    private PostgreSQLContainer postgres;

    @Override
    DataSource startDatabase() throws SQLException, IOException {
        postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
        postgres.start();
        var dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        SqlUpdater.builder(dataSource, PostgreSql.get())
                .setVersionTable("companies_db_version")
                .withClassLoader(getClass().getClassLoader())
                .setReplacements(new QueryReplacement("companies_schema", "public"))
                .execute();
        return dataSource;
    }

    @Override
    void stopDatabase() {
        postgres.stop();
    }

    @Override
    NetworkNode.Dialect dialect() {
        return NetworkNode.Dialect.POSTGRES;
    }
}
