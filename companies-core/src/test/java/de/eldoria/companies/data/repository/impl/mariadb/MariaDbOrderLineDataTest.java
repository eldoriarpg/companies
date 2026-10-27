/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sadu.mariadb.databases.MariaDb;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import de.eldoria.companies.data.repository.ADashboardData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.OrderLineDataContract;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import org.mariadb.jdbc.MariaDbDataSource;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;

import javax.sql.DataSource;

class MariaDbOrderLineDataTest extends OrderLineDataContract {
    private MariaDBContainer mariadb;

    @Override
    protected DataSource start() throws Exception {
        mariadb = new MariaDBContainer(DockerImageName.parse("mariadb:11.4"));
        mariadb.start();
        var dataSource = new MariaDbDataSource(mariadb.getJdbcUrl());
        dataSource.setUser(mariadb.getUsername());
        dataSource.setPassword(mariadb.getPassword());
        SqlUpdater.builder(dataSource, MariaDb.get())
                .setVersionTable("companies_db_version")
                .withClassLoader(getClass().getClassLoader())
                .setReplacements(new QueryReplacement("companies_schema", mariadb.getDatabaseName()))
                .execute();
        return dataSource;
    }

    @Override
    protected void stop() {
        mariadb.stop();
    }

    @Override
    protected AOrderData orders() {
        return new MariaDbOrderData(SqLiteTestDatabase.mapper());
    }

    @Override
    protected ADashboardData dashboard() {
        return new MariaDbDashboardData();
    }

    @Override
    protected Adapter<LocalDateTime> time() {
        return TimeColumns.TIMESTAMP;
    }
}
