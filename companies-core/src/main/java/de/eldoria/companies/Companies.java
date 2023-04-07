/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies;

import com.zaxxer.hikari.HikariDataSource;
import de.chojo.sadu.databases.MariaDb;
import de.chojo.sadu.databases.PostgreSql;
import de.chojo.sadu.databases.SqLite;
import de.chojo.sadu.jdbc.SqLiteJdbc;
import de.chojo.sadu.updater.BaseSqlUpdaterBuilder;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import de.chojo.sadu.wrapper.QueryBuilderConfig;
import de.eldoria.companies.api.CompaniesApiImpl;
import de.eldoria.companies.commands.Company;
import de.eldoria.companies.commands.CompanyAdmin;
import de.eldoria.companies.commands.Order;
import de.eldoria.companies.configuration.Configuration;
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
import de.eldoria.eldoutilities.config.JacksonConfig;
import de.eldoria.eldoutilities.config.template.PluginBaseConfiguration;
import de.eldoria.eldoutilities.debug.data.EntryData;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.localization.Localizer;
import de.eldoria.eldoutilities.messages.MessageSenderBuilder;
import de.eldoria.eldoutilities.plugin.EldoPlugin;
import de.eldoria.messageblocker.MessageBlockerAPI;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.milkbowl.vault.economy.Economy;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;

@SuppressWarnings("InstanceVariableMayNotBeInitialized")
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
    public Level getLogLevel() {
        return configuration.secondary(PluginBaseConfiguration.KEY).logLevel();
    }

    public Configuration configuration() {
        if(configuration == null){
        configuration = new Configuration(this);
                 }
        return configuration;
    }

    @Override
    public void onPluginLoad() throws Throwable {
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

        new MessageSenderBuilder(this)
                .errorColor(TextColor.fromHexString("#f05316"))
                .messageColor(TextColor.fromHexString("#09ad3a"))
                // TODO: Make those configurable
                .prefix("<#00a6ff>[C]")
                .addTag(builder -> builder
                        .tag("heading", Tag.styling(NamedTextColor.GOLD))
                        .tag("name", Tag.styling(NamedTextColor.AQUA))
                        .tag("value", Tag.styling(NamedTextColor.DARK_GREEN))
                        .tag("remove", Tag.styling(NamedTextColor.RED))
                        .tag("add", Tag.styling(NamedTextColor.GREEN))
                        .tag("modify", Tag.styling(NamedTextColor.YELLOW))
                        .tag("show", Tag.styling(NamedTextColor.GREEN))
                        .tag("inactive", Tag.styling(NamedTextColor.DARK_GRAY))
                        .tag("active", Tag.styling(NamedTextColor.GREEN))
                        .tag("neutral", Tag.styling(NamedTextColor.DARK_AQUA)))
                .build();
        Localizer.create(this, "en_US", "de_DE")
                .setLocale(configuration().generalSettings().language());

        var economyProvider = getServer().getServicesManager().getRegistration(Economy.class);
        if (economyProvider != null) {
            economy = economyProvider.getProvider();
        } else {
            EldoPlugin.logger().severe("No vault provider registered.");
            getPluginManager().disablePlugin(this);
            return;
        }

        messageBlocker = MessageBlockerAPI.builder(this).withExectuor(workerPool).addWhitelisted("[C]").build();

        var levelService = new LevelService(this, configuration(), companyData);

        registerCommand("company", new Company(this, companyData, orderData, economy, configuration(), messageBlocker));
        registerCommand("order", new Order(this, orderData, configuration(), economy, messageBlocker));
        registerCommand("companyadmin", new CompanyAdmin(this, configuration(), companyData, messageBlocker, levelService));

        ExpiringService.create(this, orderData, companyData, configuration(), workerPool);
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

    private void initDb() throws SQLException, IOException {
        dataSource = DataSourceFactory.createDataSource(configuration.databaseSettings(), this);

        BaseSqlUpdaterBuilder<?, ?> builder;

        switch (configuration.databaseSettings().storageType()) {
            case SQLITE -> {
                getLogger().info("Using SqLite database");
                builder = SqlUpdater.builder(dataSource, SqLite.get());
            }
            case MARIADB -> {
                getLogger().info("Using MariaDB database");
                builder = SqlUpdater.builder(dataSource, MariaDb.get());
            }
            case POSTGRES -> {
                getLogger().info("Using Postgres database");
                builder = SqlUpdater.builder(dataSource, PostgreSql.get());
            }
            default ->
                    throw new IllegalStateException("Unexpected value: " + configuration.databaseSettings().storageType());
        }

        builder.setVersionTable("companies_db_version")
                .setReplacements(new QueryReplacement("companies_schema", configuration.databaseSettings().schema()))
                .withConfig(QueryBuilderConfig.builder().withExceptionHandler(err -> getLogger().log(Level.SEVERE, "Error during update", err)).build())
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

    private ThreadFactory createThreadFactory(ThreadGroup group) {
        return r -> {
            var thread = new Thread(group, r, group.getName());
            thread.setUncaughtExceptionHandler(exceptionHandler);
            return thread;
        };
    }
}
