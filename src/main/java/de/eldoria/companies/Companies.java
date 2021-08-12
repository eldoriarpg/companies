package de.eldoria.companies;

import com.zaxxer.hikari.HikariDataSource;
import de.chojo.sqlutil.datasource.DataSourceCreator;
import de.chojo.sqlutil.updater.SqlType;
import de.chojo.sqlutil.updater.SqlUpdater;
import de.chojo.sqlutil.updater.logging.JavaLogger;
import de.eldoria.companies.commands.Company;
import de.eldoria.companies.commands.company.Order;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.DatabaseSettings;
import de.eldoria.companies.configuration.elements.GeneralSettings;
import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.configuration.elements.UserSettings;
import de.eldoria.companies.data.repository.impl.MariaDbCompanyData;
import de.eldoria.companies.data.repository.impl.MariaDbOrderData;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.plugin.EldoPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.mariadb.jdbc.MariaDbDataSource;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;

public class Companies extends EldoPlugin {
    private Configuration configuration;
    private DataSource dataSource;
    private ACompanyData companyData;
    private AOrderData orderData;
    private Economy economy;

    @Override
    public void onPluginEnable() {
        configuration = new Configuration(this);

        MessageSender.create(this, "§c[C]");
        ILocalizer.create(this, "en_US", "de_DE").setLocale("en_US");

        var economyProvider = getServer().getServicesManager().getRegistration(Economy.class);
        if (economyProvider != null) {
            economy = economyProvider.getProvider();
        } else {
            logger().severe("No vault provider registered.");
            getPluginManager().disablePlugin(this);
            return;
        }

        try {
            initDb();
        } catch (SQLException | IOException e) {
            logger().log(Level.SEVERE, "Could not init database", e);
            getPluginManager().disablePlugin(this);
        }

        registerCommand("company", new Company(this, companyData, orderData, economy, configuration));
        registerCommand("order", new Order(this, companyData, orderData, economy, configuration));

    }

    @Override
    public void onPluginDisable() {

    }

    @Override
    public List<Class<? extends ConfigurationSerializable>> getConfigSerialization() {
        return List.of(CompanySettings.class, DatabaseSettings.class, GeneralSettings.class, OrderSettings.class, UserSettings.class);
    }

    private void initDb() throws SQLException, IOException {
        buildDataSource();

        SqlUpdater.SqlUpdaterBuilder builder = null;

        switch (configuration.databaseSettings().storageType()) {
            case SQLITE:
                builder = SqlUpdater
                        .builder(dataSource, SqlType.SQLITE);
                break;
            case MARIADB:
                builder = SqlUpdater
                        .builder(dataSource, SqlType.MARIADB);
                break;
            case POSTGRES:
                builder = SqlUpdater
                        .builder(dataSource, SqlType.POSTGRES);
                throw new IllegalStateException("Not Implemented yet");
        }

        builder.setVersionTable("order_db_version")
                .withLogger(new JavaLogger(logger()))
                .execute();


        initDataRepositories();
    }

    private void buildDataSource() throws SQLException {
        var db = configuration.databaseSettings();
        switch (db.storageType()) {
            case SQLITE:
                var path = getDataFolder().toPath().resolve(Paths.get("data.db"));
                try {
                    path = Files.createFile(path);
                } catch (FileAlreadyExistsException e) {
                    logger().info("Found sqlite database file.");
                } catch (IOException e) {
                    logger().log(Level.SEVERE, "Could not create database file", e);
                    throw new IllegalStateException("Failed to init Database");
                }
                var sqLiteDataSource = new SQLiteDataSource();
                sqLiteDataSource.setUrl("jdbc:sqlite:" + path.toString());
                dataSource = sqLiteDataSource;

                var hikariDataSource = new HikariDataSource();
                hikariDataSource.setConnectionTestQuery("SELECT 1");
                hikariDataSource.setDataSource(dataSource);
                dataSource = hikariDataSource;
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
                // TODO: probably implement in the future
                dataSource = DataSourceCreator
                        .create(MariaDbDataSource.class)
                        .withAddress(db.host())
                        .withPort(db.port())
                        .forDatabase(db.database())
                        .withUser(db.user())
                        .withPassword(db.password())
                        .create()
                        .forSchema(db.schema())
                        .build();
                throw new IllegalStateException("Not Implemented yet");
        }

        try (var conn = dataSource.getConnection(); var stmt = conn.prepareStatement("SELECT 1")) {
            stmt.execute();
        }
    }

    private void initDataRepositories() {
        switch (configuration.databaseSettings().storageType()) {
            case SQLITE:
            case MARIADB:
                companyData = new MariaDbCompanyData(dataSource, this);
                orderData = new MariaDbOrderData(dataSource, this);
                break;
            case POSTGRES:
                break;
        }
    }
}
