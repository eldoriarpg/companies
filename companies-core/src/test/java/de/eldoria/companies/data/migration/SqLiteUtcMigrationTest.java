/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.migration;

import de.chojo.sadu.sqlite.databases.SqLite;
import de.chojo.sadu.updater.BaseSqlUpdaterBuilder;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;

import javax.sql.DataSource;

class SqLiteUtcMigrationTest extends UtcMigrationContract {
    @TempDir
    Path directory;

    @Override
    protected DataSource start() {
        var dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + directory.resolve("data.db"));
        return dataSource;
    }

    @Override
    protected BaseSqlUpdaterBuilder<?, ?> updater(DataSource dataSource) {
        return SqlUpdater.builder(dataSource, SqLite.get())
                .setVersionTable("companies_db_version")
                .withClassLoader(getClass().getClassLoader())
                .setReplacements(new QueryReplacement("companies_schema", "companies_schema"));
    }

    @Override
    protected UtcMigration.Dialect dialect() {
        return UtcMigration.Dialect.SQLITE;
    }

    @Override
    protected String schema() {
        return "";
    }

    @Override
    protected String literal(LocalDateTime time) {
        return "'" + time.toString().replace('T', ' ') + ":00'";
    }

    @Override
    protected LocalDateTime time(DataSource dataSource, String query) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery(query)) {
            if (!rows.next()) return null;
            var text = rows.getString(1);
            return text == null ? null : LocalDateTime.parse(text.replace(' ', 'T'));
        }
    }

    @Override
    protected boolean databaseTimesConverted() {
        return false;
    }
}
