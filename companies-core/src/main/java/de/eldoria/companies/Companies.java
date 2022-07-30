package de.eldoria.companies;

import com.comphenix.protocol.ProtocolManager;
import com.zaxxer.hikari.HikariDataSource;
import de.chojo.sqlutil.databases.SqlType;
import de.chojo.sqlutil.logging.JavaLogger;
import de.chojo.sqlutil.updater.QueryReplacement;
import de.chojo.sqlutil.updater.SqlUpdater;
import de.eldoria.companies.api.CompaniesApiImpl;
import de.eldoria.companies.commands.Company;
import de.eldoria.companies.commands.CompanyAdmin;
import de.eldoria.companies.commands.Order;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.DatabaseSettings;
import de.eldoria.companies.configuration.elements.GeneralSettings;
import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.configuration.elements.UserSettings;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.configuration.elements.companylevel.LevelRequirement;
import de.eldoria.companies.configuration.elements.companylevel.LevelSettings;
import de.eldoria.companies.data.DataSourceFactory;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.ANotificationData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbCompanyData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbNotificationData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbOrderData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresCompanyData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresNotificationData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteCompanyData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiterNotificationData;
import de.eldoria.companies.services.ExpiringService;
import de.eldoria.companies.services.LevelService;
import de.eldoria.companies.services.PlaceholderService;
import de.eldoria.companies.services.RefreshService;
import de.eldoria.companies.services.notifications.NotificationService;
import de.eldoria.companies.util.UserData;
import de.eldoria.eldoutilities.debug.data.EntryData;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.plugin.EldoPlugin;
import de.eldoria.messageblocker.MessageBlockerAPI;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;

public class Companies extends EldoPlugin {
    private final Thread.UncaughtExceptionHandler exceptionHandler =
            (t, e) -> getLogger().log(Level.SEVERE, "An uncaught exception occured in " + t.getName() + "-" + t.getId() + ".", e);
    private final ThreadGroup workerGroup = new ThreadGroup("Company Worker Pool");
    private final ScheduledExecutorService workerPool = Executors.newScheduledThreadPool(5, createThreadFactory(workerGroup));
    private Configuration configuration;
    private HikariDataSource dataSource;
    private ACompanyData companyData;
    private AOrderData orderData;
    private ANotificationData notificationData;
    private Economy economy;
    private MessageBlocker messageBlocker;

    @Override
    public void onPluginLoad() throws Throwable {
        configuration = new Configuration(this);
        try {
            initDb();
        } catch (SQLException | IOException e) {
            EldoPlugin.logger().log(Level.SEVERE, "Could not init database", e);
            throw e;
        }

        CompaniesApiImpl.create(companyData, orderData);
    }

    @Override
    public void onPluginEnable(boolean reload) throws Exception {

        // TODO: Make those configurable
        MessageSender.create(this, "§c[C]");
        ILocalizer.create(this, "en_US", "de_DE").setLocale(configuration.generalSettings().language());

        var economyProvider = getServer().getServicesManager().getRegistration(Economy.class);
        if (economyProvider != null) {
            economy = economyProvider.getProvider();
        } else {
            EldoPlugin.logger().severe("No vault provider registered.");
            getPluginManager().disablePlugin(this);
            return;
        }

        messageBlocker = MessageBlockerAPI.builder(this).withExectuor(workerPool).addWhitelisted("[C]").build();

        var levelService = new LevelService(this, configuration, companyData);

        registerCommand("company", new Company(this, companyData, orderData, economy, configuration, messageBlocker));
        registerCommand("order", new Order(this, orderData, configuration, economy, messageBlocker));
        registerCommand("companyadmin", new CompanyAdmin(this, configuration, companyData, messageBlocker, levelService));

        ExpiringService.create(this, orderData, companyData, configuration, workerPool);
        RefreshService.create(orderData, workerPool);
        registerListener(levelService);
        registerListener(new NotificationService(notificationData, orderData, workerPool, this));

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            var placeholderService = new PlaceholderService(this, companyData, orderData);
            placeholderService.register();
            registerListener(placeholderService);
        }
    }

    @Override
    public void onPluginDisable() {
        workerPool.shutdown();
        dataSource.close();
    }

    @Override
    public List<Class<? extends ConfigurationSerializable>> getConfigSerialization() {
        return List.of(CompanySettings.class, DatabaseSettings.class, GeneralSettings.class, OrderSettings.class, UserSettings.class,
                CompanyLevel.class, LevelSettings.class, LevelRequirement.class);
    }

    private void initDb() throws SQLException, IOException {
        dataSource = DataSourceFactory.createDataSource(configuration.databaseSettings(), this);

        SqlUpdater.SqlUpdaterBuilder<?> builder;

        switch (configuration.databaseSettings().storageType()) {
            case SQLITE -> {
                getLogger().info("Using SqLite database");
                builder = SqlUpdater.builder(dataSource, SqlType.SQLITE);
            }
            case MARIADB -> {
                getLogger().info("Using MariaDB database");
                builder = SqlUpdater.builder(dataSource, SqlType.MARIADB);
            }
            case POSTGRES -> {
                getLogger().info("Using Postgres database");
                builder = SqlUpdater.builder(dataSource, SqlType.POSTGRES);
            }
            default ->
                    throw new IllegalStateException("Unexpected value: " + configuration.databaseSettings().storageType());
        }

        builder.setVersionTable("companies_db_version")
                .setReplacements(new QueryReplacement("companies_schema", configuration.databaseSettings().schema()))
                .withLogger(new JavaLogger(getLogger()))
                .execute();

        initDataRepositories();
    }

    private void initDataRepositories() {
        switch (configuration.databaseSettings().storageType()) {
            case SQLITE:
                companyData = new SqLiteCompanyData(dataSource, this, workerPool);
                orderData = new SqLiteOrderData(dataSource, this, workerPool);
                notificationData = new SqLiterNotificationData(dataSource, this, workerPool);
                break;
            case MARIADB:
                companyData = new MariaDbCompanyData(dataSource, this, workerPool);
                orderData = new MariaDbOrderData(dataSource, this, workerPool);
                notificationData = new MariaDbNotificationData(dataSource, this, workerPool);
                break;
            case POSTGRES:
                companyData = new PostgresCompanyData(dataSource, this, workerPool);
                orderData = new PostgresOrderData(dataSource, this, workerPool);
                notificationData = new PostgresNotificationData(dataSource, this, workerPool);
            default:
                throw new IllegalStateException("Unexpected value: " + configuration.databaseSettings().storageType());
        }
    }

    @Override
    public @NotNull EntryData[] getDebugInformations() {
        return new EntryData[]{new EntryData("Customer Data", UserData.get(this).asString())};
    }

    private ThreadFactory createThreadFactory(ThreadGroup group) {
        return r -> {
            var thread = new Thread(group, r, group.getName());
            thread.setUncaughtExceptionHandler(exceptionHandler);
            return thread;
        };
    }
}
