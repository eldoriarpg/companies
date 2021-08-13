package de.eldoria.companies.data;

import de.chojo.sqlutil.datasource.DataSourceCreator;
import de.eldoria.companies.configuration.elements.DatabaseSettings;
import org.bukkit.plugin.Plugin;
import org.mariadb.jdbc.MariaDbDataSource;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.logging.Level;

public final class DataSourceFactory {
    public static DataSource createDataSource(DatabaseSettings db, Plugin plugin) throws SQLException {
        DataSource dataSource;
        switch (db.storageType()) {
            case SQLITE:
                var path = plugin.getDataFolder().toPath().resolve(Paths.get("data.db"));
                try {
                    path = Files.createFile(path);
                } catch (FileAlreadyExistsException e) {
                    plugin.getLogger().info("Found sqlite database file.");
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not create database file", e);
                    throw new IllegalStateException("Failed to init Database");
                }
                var sqLiteDataSource = new SQLiteDataSource();
                sqLiteDataSource.setUrl("jdbc:sqlite:" + path.toString());

                dataSource = sqLiteDataSource;
                break;
            case MARIADB:
                dataSource = DataSourceCreator
                        .create(MariaDbDataSource.class)
                        .withAddress(db.host())
                        .withPort(db.port())
                        .forDatabase(db.database())
                        .withUser(db.user())
                        .withPassword(db.password())
                        .create()
                        .build();
                break;
            case POSTGRES:
                throw new IllegalStateException("Postgres is not supported yet.");
            default:
                throw new IllegalStateException("Unkown Database Type: " + db.storageType());
        }

        try (var conn = dataSource.getConnection(); var stmt = conn.prepareStatement("SELECT 1")) {
            stmt.execute();
        }
        return dataSource;
    }
}
