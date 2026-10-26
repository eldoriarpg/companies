/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.postgres;

import de.chojo.sadu.postgresql.databases.PostgreSql;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import de.eldoria.companies.data.repository.ADashboardData;
import de.eldoria.companies.data.repository.AHistoryData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.SystemOrderDataContract;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;

import javax.sql.DataSource;

class PostgresSystemOrderDataTest extends SystemOrderDataContract {
    private PostgreSQLContainer postgres;

    @Override
    protected DataSource start() throws Exception {
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
    protected void stop() {
        postgres.stop();
    }

    @Override
    protected AOrderData orders() {
        return new PostgresOrderData(null, SqLiteTestDatabase.mapper());
    }

    @Override
    protected AHistoryData history(ExecutorService executor) {
        return new PostgresHistoryData(SqLiteTestDatabase.mapper(), executor);
    }

    @Override
    protected ADashboardData dashboard() {
        return new PostgresDashboardData();
    }

    @Override
    protected Adapter<LocalDateTime> time() {
        return TimeColumns.TIMESTAMP;
    }
}
