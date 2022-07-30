package de.eldoria.companies.data;

import com.zaxxer.hikari.HikariDataSource;
import de.chojo.sqlutil.databases.SqlType;
import de.chojo.sqlutil.datasource.DataSourceCreator;
import de.eldoria.companies.configuration.elements.DatabaseSettings;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.logging.Level;

public final class DataSourceFactory {
    private DataSourceFactory() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    public static HikariDataSource createDataSource(DatabaseSettings db, Plugin plugin) throws SQLException {
        HikariDataSource dataSource;
        switch (db.storageType()) {
            case SQLITE -> {
                var path = plugin.getDataFolder().toPath().resolve(Paths.get("data.db"));
                try {
                    Files.createFile(path);
                } catch (FileAlreadyExistsException e) {
                    plugin.getLogger().info("Found sqlite database file.");
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not create database file", e);
                    throw new IllegalStateException("Failed to init Database");
                }
                dataSource = DataSourceCreator.create(SqlType.SQLITE)
                        .configure(config -> config.path(path))
                        .create()
                        .build();
            }
            case MARIADB -> dataSource = DataSourceCreator
                    .create(SqlType.MARIADB)
                    .configure(config -> config.host(db.host())
                            .port(db.port())
                            .database(db.database())
                            .user(db.user())
                            .password(db.password()))
                    .create()
                    .withMaximumPoolSize(5)
                    .withMinimumIdle(2)
                    .build();
            case POSTGRES -> dataSource = DataSourceCreator
                    .create(SqlType.POSTGRES)
                    .configure(config -> config
                            .host(db.host())
                            .port(db.port())
                            .database(db.database())
                            .user(db.user())
                            .password(db.password()))
                    .create()
                    .withMaximumPoolSize(5)
                    .withMinimumIdle(2)
                    .forSchema(db.schema())
                    .build();
            default -> throw new IllegalStateException("Unkown Database Type: " + db.storageType());
        }

        try (var conn = dataSource.getConnection(); var stmt = conn.prepareStatement("SELECT 1")) {
            stmt.execute();
        }
        return dataSource;
    }
}
