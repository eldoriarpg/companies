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
import de.eldoria.companies.services.ExpiringService;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.plugin.EldoPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;

public class Companies extends EldoPlugin {
    private Configuration configuration = null;
    private DataSource dataSource = null;
    private ACompanyData companyData = null;
    private AOrderData orderData = null;
    private Economy economy;
    private final Thread.UncaughtExceptionHandler exceptionHandler =
            (t, e) -> getLogger().log(Level.SEVERE, "An uncaught exception occured in " + t.getName() + "-" + t.getId() + ".", e);
    private final ThreadGroup workerGroup = new ThreadGroup("Company Worker Pool");
    private final ScheduledExecutorService workerPool = Executors.newScheduledThreadPool(5, createThreadFactory(workerGroup));

    @Override
    public void onPluginEnable(boolean reload) throws Exception {
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
            throw e;
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
        dataSource = DataSourceFactory.createDataSource(configuration.databaseSettings(), this);

        SqlUpdater.SqlUpdaterBuilder builder;

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

        builder.setVersionTable("companies_db_version")
                .withLogger(new JavaLogger(logger()))
                .execute();


        initDataRepositories();
        ExpiringService.create(orderData, configuration, workerPool);
    }

    private void initDataRepositories() {
        switch (configuration.databaseSettings().storageType()) {
            case SQLITE:
                companyData = new SqLiteCompanyData(dataSource, this, workerPool);
                orderData = new SqLiteOrderData(dataSource, this, workerPool);
                break;
            case MARIADB:
                companyData = new MariaDbCompanyData(dataSource, this, workerPool);
                orderData = new MariaDbOrderData(dataSource, this, workerPool);
                break;
            case POSTGRES:
                throw new IllegalStateException("Not Implemented yet");
            default:
                throw new IllegalStateException("Unexpected value: " + configuration.databaseSettings().storageType());
        }
    }

    private ThreadFactory createThreadFactory(ThreadGroup group) {
        return r -> {
            var thread = new Thread(group, r, group.getName());
            thread.setUncaughtExceptionHandler(exceptionHandler);
            return thread;
        };
    }
}
