/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.zaxxer.hikari.HikariDataSource;
import de.chojo.sadu.mariadb.databases.MariaDb;
import de.chojo.sadu.postgresql.databases.PostgreSql;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.chojo.sadu.sqlite.databases.SqLite;
import de.chojo.sadu.updater.BaseSqlUpdaterBuilder;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import de.eldoria.companies.api.CompaniesApiImpl;
import de.eldoria.companies.commands.Company;
import de.eldoria.companies.commands.CompanyAdmin;
import de.eldoria.companies.commands.Order;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.data.DataSourceFactory;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.companies.data.repository.ANotificationData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbCompanyData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbNodeData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbNotificationData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbOrderData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresCompanyData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresNodeData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresNotificationData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteCompanyData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteNodeData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteNotificationData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.services.ExpiringService;
import de.eldoria.companies.services.LevelService;
import de.eldoria.companies.services.PlaceholderService;
import de.eldoria.companies.services.RefreshService;
import de.eldoria.companies.services.notifications.NotificationService;
import de.eldoria.companies.util.Permission;
import de.eldoria.eldoutilities.config.template.PluginBaseConfiguration;
import de.eldoria.eldoutilities.debug.DebugDataProvider;
import de.eldoria.eldoutilities.localization.Localizer;
import de.eldoria.eldoutilities.messages.MessageSenderBuilder;
import de.eldoria.eldoutilities.plugin.EldoPlugin;
import de.eldoria.eldoutilities.updater.lynaupdater.LynaUpdateChecker;
import de.eldoria.eldoutilities.updater.lynaupdater.LynaUpdateData;
import de.eldoria.messageblocker.MessageBlockerAPI;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.milkbowl.vault.economy.Economy;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;

@SuppressWarnings("InstanceVariableMayNotBeInitialized")
public class Companies extends EldoPlugin {
    private final Thread.UncaughtExceptionHandler exceptionHandler =
            (t, e) -> getLogger().log(Level.SEVERE, "An uncaught exception occurred in " + t.getName() + "-" + t.getId() + ".", e);
    private final ThreadGroup workerGroup = new ThreadGroup("Company Worker Pool");
    private final ScheduledExecutorService workerPool = Executors.newScheduledThreadPool(5, createThreadFactory(workerGroup));
    private final Configuration configuration;
    private HikariDataSource dataSource;
    private ACompanyData companyData;
    private AOrderData orderData;
    private ANotificationData notificationData;
    private ANodeData nodeData;

    public Companies() {
        configuration = new Configuration(this);
        configuration.secondary(PluginBaseConfiguration.KEY);
    }

    @Override
    public Level getLogLevel() {
        if (!configuration.exists(PluginBaseConfiguration.KEY)) {
            return PluginBaseConfiguration.KEY.initValue().get().logLevel();
        }
        return configuration.secondary(PluginBaseConfiguration.KEY).logLevel();
    }

    @Override
    public void onPluginLoad() throws SQLException, IOException {
        try {
            initDb();
        } catch (SQLException | IOException e) {
            EldoPlugin.logger().log(Level.SEVERE, "Could not init database", e);
            throw e;
        }

        configuration.syncConfigurations(nodeData).join();

        CompaniesApiImpl.create(companyData, orderData);
    }

    @Override
    public void onPluginEnable(boolean reload) {

        var localizer = Localizer.builder(this, "en_US")
                                 .setIncludedLocales("en_US", "de_DE", "fr_FR")
                                 .setUserLocale(p -> configuration.generalSettings().language())
                                 .build();

        new MessageSenderBuilder(this)
                .errorColor(TextColor.fromHexString("#f05316"))
                .messageColor(NamedTextColor.DARK_AQUA)
                // TODO: Make those configurable
                .prefix("<#00a6ff>[C]")
                .localizer(localizer)
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
                .register();

        var economyProvider = getServer().getServicesManager().getRegistration(Economy.class);
        Economy economy;
        if (economyProvider != null) {
            economy = economyProvider.getProvider();
        } else {
            getLogger().severe("No vault provider registered.");
            getPluginManager().disablePlugin(this);
            return;
        }

        var messageBlocker = MessageBlockerAPI.builder(this)
                                              .withExecutor(workerPool)
                                              .addWhitelisted("[C]")
                                              .build();

        var levelService = new LevelService(this, configuration, companyData);

        if (configuration.generalSettings().checkUpdates()) {
            LynaUpdateChecker.lyna(LynaUpdateData.builder(this, 2)
                                                 .updateUrl("https://companies.discord.eldoria.de/")
                                                 .notifyPermission(Permission.Admin.ADMIN)
                                                 .build()).start();
        }

        registerCommand("company", new Company(this, companyData, orderData, economy, configuration, messageBlocker));
        registerCommand("order", new Order(this, orderData, configuration, economy, messageBlocker));
        registerCommand("companyadmin", new CompanyAdmin(this, configuration, companyData, messageBlocker, levelService, nodeData));

        if (configuration.nodeSettings().nodeType() == NodeType.PRIMARY) {
            ExpiringService.start(this, orderData, companyData, configuration, workerPool);
            RefreshService.start(orderData, workerPool);
        }
        registerListener(levelService);
        registerListener(new NotificationService(notificationData, orderData, workerPool, this));

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI") && configuration.generalSettings().hooks().placeholderapi()) {
            var placeholderService = new PlaceholderService(this, companyData, orderData);
            placeholderService.register();
            registerListener(placeholderService);
        }
    }

    @Override
    public @NotNull List<DebugDataProvider> getDebugProviders() {
        return List.of(configuration);
    }

    @Override
    public void onPluginDisable() {
        workerPool.shutdown();
        dataSource.close();
    }

    private void initDb() throws SQLException, IOException {
        dataSource = DataSourceFactory.createDataSource(configuration.databaseSettings(), this);
        QueryConfiguration.setDefault(QueryConfiguration.builder(dataSource)
                                                        .setExceptionHandler(err -> getLogger().log(Level.SEVERE, "Error during query execution", err))
                                                        .build());

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
            default -> {
                throw new IllegalStateException("Unexpected value: " + configuration.databaseSettings().storageType());
            }
        }

        builder.setVersionTable("companies_db_version")
                .withClassLoader(this.getClass().getClassLoader())
               .setReplacements(new QueryReplacement("companies_schema", configuration.databaseSettings().schema()))
               .execute();

        initDataRepositories();
    }

    private void initDataRepositories() {
        var mapper = configuration.configureDefault(JsonMapper.builder());
        switch (configuration.databaseSettings()
                             .storageType()) {
            case SQLITE:
                companyData = new SqLiteCompanyData(workerPool);
                orderData = new SqLiteOrderData(workerPool, mapper);
                notificationData = new SqLiteNotificationData(workerPool);
                nodeData = new SqLiteNodeData(configuration.nodeSettings());
                break;
            case MARIADB:
                companyData = new MariaDbCompanyData(workerPool);
                orderData = new MariaDbOrderData(mapper);
                notificationData = new MariaDbNotificationData(workerPool);
                nodeData = new MariaDbNodeData(configuration.nodeSettings());
                break;
            case POSTGRES:
                companyData = new PostgresCompanyData(workerPool);
                orderData = new PostgresOrderData(workerPool, mapper);
                notificationData = new PostgresNotificationData(workerPool);
                nodeData = new PostgresNodeData(configuration.nodeSettings());
            default:
                throw new IllegalStateException("Unexpected value: " + configuration.databaseSettings()
                                                                                    .storageType());
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
