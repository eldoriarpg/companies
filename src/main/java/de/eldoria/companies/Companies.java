package de.eldoria.companies;

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
import de.eldoria.companies.data.DataSourceFactory;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.impl.MariaDbCompanyData;
import de.eldoria.companies.data.repository.impl.MariaDbOrderData;
import de.eldoria.companies.data.repository.impl.SqLiteCompanyData;
import de.eldoria.companies.data.repository.impl.SqLiteOrderData;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.plugin.EldoPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import javax.sql.DataSource;
import java.io.IOException;
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
                builder = SqlUpdater.builder(dataSource, SqlType.SQLITE);
                break;
            case MARIADB:
                builder = SqlUpdater.builder(dataSource, SqlType.MARIADB);
                break;
            case POSTGRES:
                throw new IllegalStateException("Not Implemented yet");
            default:
                throw new IllegalStateException("Unexpected value: " + configuration.databaseSettings().storageType());
        }

        builder.setVersionTable("order_db_version")
                .withLogger(new JavaLogger(logger()))
                .execute();


        initDataRepositories();
    }

    private void buildDataSource() throws SQLException {
        dataSource = DataSourceFactory.createDataSource(configuration.databaseSettings(), this);
    }

    private void initDataRepositories() {
        switch (configuration.databaseSettings().storageType()) {
            case SQLITE:
                companyData = new SqLiteCompanyData(dataSource, this);
                orderData = new SqLiteOrderData(dataSource, this);
                break;
            case MARIADB:
                companyData = new MariaDbCompanyData(dataSource, this);
                orderData = new MariaDbOrderData(dataSource, this);
                break;
            case POSTGRES:
                throw new IllegalStateException("Not Implemented yet");
            default:
                throw new IllegalStateException("Unexpected value: " + configuration.databaseSettings().storageType());
        }
    }
}
