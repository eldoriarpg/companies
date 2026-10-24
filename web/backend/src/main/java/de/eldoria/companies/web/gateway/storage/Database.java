/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.storage;

import de.chojo.sadu.datasource.DataSourceCreator;
import de.chojo.sadu.mapper.RowMapperRegistry;
import de.chojo.sadu.postgresql.databases.PostgreSql;
import de.chojo.sadu.postgresql.mapper.PostgresqlMapper;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import de.eldoria.companies.web.gateway.ServiceConfig;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

/**
 * Connects to PostgreSQL through a HikariCP pool, migrates the schema and installs the default query configuration.
 */
public final class Database {
    private static final Logger log = LoggerFactory.getLogger(Database.class);
    private static volatile @Nullable DataSource opened;

    private Database() {}

    /**
     * Asks the database whether it answers, without the query configuration, so a failure is not logged as a failed
     * query.
     *
     * @return whether the database of the last {@link #open(ServiceConfig.DatabaseConfig)} answers
     */
    public static boolean ping() {
        DataSource current = opened;
        if (current == null) return false;
        try (Connection connection = current.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException | RuntimeException e) {
            return false;
        }
    }

    /**
     * @param config the connection
     * @return the pool, migrated
     * @throws SQLException when the database cannot be reached or migrated
     * @throws IOException when a migration script cannot be read
     */
    public static DataSource open(ServiceConfig.DatabaseConfig config) throws SQLException, IOException {
        DataSource source = DataSourceCreator.create(PostgreSql.get())
                .configure(builder -> builder.host(config.host())
                        .port(config.port())
                        .database(config.name())
                        .user(config.user())
                        .password(config.password())
                        .currentSchema(config.schema())
                        .applicationName("Companies Gateway"))
                .create()
                .withMaximumPoolSize(10)
                .withMinimumIdle(1)
                .build();
        SqlUpdater.builder(source, PostgreSql.get())
                .setReplacements(new QueryReplacement("web_schema", config.schema()))
                .setSchemas(config.schema())
                .execute();
        QueryConfiguration.setDefault(QueryConfiguration.builder(source)
                .setExceptionHandler(error -> log.error("Database query failed", error))
                .setThrowExceptions(true)
                .setRowMapperRegistry(new RowMapperRegistry().register(PostgresqlMapper.getDefaultMapper()))
                .build());
        opened = source;
        return source;
    }
}
