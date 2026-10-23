/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.chojo.sadu.mariadb.databases.MariaDb;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import org.mariadb.jdbc.MariaDbDataSource;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.sql.SQLException;

import javax.sql.DataSource;

/**
 * Low latency mode on MariaDB.
 */
class MariaDbLowLatencyIntegrationTest extends LowLatencyIntegrationTest {
    private MariaDBContainer mariadb;

    @Override
    DataSource startDatabase() throws SQLException, IOException {
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
    void stopDatabase() {
        mariadb.stop();
    }

    @Override
    NetworkNode.Dialect dialect() {
        return NetworkNode.Dialect.MARIADB;
    }
}
