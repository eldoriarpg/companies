/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies;

import com.zaxxer.hikari.HikariDataSource;
import de.chojo.sadu.mariadb.databases.MariaDb;
import de.chojo.sadu.postgresql.databases.PostgreSql;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.chojo.sadu.sqlite.databases.SqLite;
import de.chojo.sadu.updater.BaseSqlUpdaterBuilder;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import de.eldoria.companies.api.CompaniesApiImpl;
import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.CommandRegistration;
import de.eldoria.companies.commands.suggest.CompanyOrderSources;
import de.eldoria.companies.commands.suggest.MembershipSources;
import de.eldoria.companies.commands.suggest.SuggestionSources;
import de.eldoria.companies.commands.support.CommandFailures;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.data.DataSourceFactory;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AMembershipData;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.companies.data.repository.ANotificationData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.InformationData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbCompanyData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbHistoryData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbInboxData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbMembershipData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbNodeData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbNotificationData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbOrderData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbStatsData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresCompanyData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresHistoryData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresInboxData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresMembershipData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresNodeData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresNotificationData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresOrderData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresStatsData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteCompanyData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteHistoryData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteInboxData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteMembershipData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteNodeData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteNotificationData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteStatsData;
import de.eldoria.companies.debug.CompaniesDebug;
import de.eldoria.companies.menus.MenuService;
import de.eldoria.companies.orders.DeliveryService;
import de.eldoria.companies.services.DisbandService;
import de.eldoria.companies.services.ExpiringService;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.services.LevelService;
import de.eldoria.companies.services.MembershipService;
import de.eldoria.companies.services.PlaceholderService;
import de.eldoria.companies.services.RefreshService;
import de.eldoria.companies.services.notifications.NotificationService;
import de.eldoria.companies.updates.Updates;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.config.template.PluginBaseConfiguration;
import de.eldoria.eldoutilities.debug.DebugDataProvider;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.localization.Localizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.plugin.EldoPlugin;
import de.eldoria.messageblocker.MessageBlockerAPI;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.milkbowl.vault.economy.Economy;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@SuppressWarnings("InstanceVariableMayNotBeInitialized")
public class Companies extends EldoPlugin {
    private final Thread.UncaughtExceptionHandler exceptionHandler = (t, e) -> getLogger()
            .log(Level.SEVERE, "An uncaught exception occurred in " + t.getName() + "-" + t.threadId() + ".", e);
    private final ThreadGroup workerGroup = new ThreadGroup("Company Worker Pool");
    private final ScheduledExecutorService workerPool =
            Executors.newScheduledThreadPool(5, createThreadFactory(workerGroup));
    private final Configuration configuration;
    private HikariDataSource dataSource;
    private ACompanyData companyData;
    private AOrderData orderData;
    private ANotificationData notificationData;
    private InformationData information;
    private ANodeData nodeData;
    private AMembershipData membershipData;
    private Updates updates;
    private CommandDependencies commands;

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

        StyledMessageSender.register(this, localizer, configuration::styleSettings);
        registerCommands();
    }

    /**
     * Registers the command trees with the commands lifecycle event. The commands refuse until the post start hands
     * them the services built on the economy.
     */
    private void registerCommands() {
        var dependencies = new CommandDependencies(
                this, configuration, companyData, orderData, nodeData, membershipData, information);
        commands = dependencies;
        CommandFailures.use((sender, failure) -> failure.sendTo(sender, MessageSender.getPluginMessageSender(this)));
        SuggestionSources.use(new SuggestionSources(
                companyData,
                orderData,
                dependencies.drafts(),
                configuration,
                ILocalizer.getPluginLocalizer(this),
                Threading.VIRTUAL));
        MembershipSources.use(new MembershipSources(companyData, membershipData, Threading.VIRTUAL));
        CompanyOrderSources.use(
                new CompanyOrderSources(companyData, orderData, dependencies.searches(), Threading.VIRTUAL));
        getLifecycleManager()
                .registerEventHandler(LifecycleEvents.COMMANDS.newHandler(
                        event -> CommandRegistration.register(event.registrar(), dependencies)));
    }

    /**
     * Sets up everything that needs the Vault economy. This runs one tick after enabling, once every plugin is
     * enabled, because economy plugins may register their provider after this plugin. Without a provider the plugin
     * fails into the failsafe mode instead of disabling itself.
     */
    @Override
    public void onPostStart(boolean reload) {
        var economyProvider = getServer().getServicesManager().getRegistration(Economy.class);
        if (economyProvider == null) {
            throw new IllegalStateException(
                    "No Vault economy provider is registered. Install an economy plugin that supports Vault.");
        }
        var economy = economyProvider.getProvider();

        MessageBlocker messageBlocker;
        if (configuration.main().generalSettings().messageBlocking()) {
            messageBlocker =
                    MessageBlockerAPI.builder(this).executor(workerPool).build();
        } else {
            messageBlocker = MessageBlocker.dummy(this);
        }

        var levelService = new LevelService(this, configuration, companyData);

        updates = new Updates(this, configuration);
        updates.apply();

        var itemReturnService = new ItemReturnService(this, orderData);

        var notificationService = new NotificationService(notificationData, orderData, workerPool, this);
        var disbandService = new DisbandService(this, companyData, orderData, itemReturnService, notificationService);
        var membershipService = MembershipService.create(
                this,
                companyData,
                membershipData,
                configuration::companySettings,
                notificationService,
                Threading.VIRTUAL);

        if (commands != null) {
            commands.provide(new CommandDependencies.Services(
                    economy,
                    messageBlocker,
                    itemReturnService,
                    levelService,
                    notificationService,
                    disbandService,
                    updates,
                    new DeliveryService(this, orderData, information.stats(), economy, configuration),
                    new MenuService(
                            messageBlocker, () -> configuration.menuSettings().mode()),
                    membershipService));
        }

        var expiringService = new ExpiringService(
                this, orderData, companyData, configuration, itemReturnService, economy, notificationService);
        if (configuration.nodeSettings().nodeType() == NodeType.PRIMARY) {
            expiringService.start(workerPool);
            RefreshService.start(orderData, workerPool);
            membershipService.start(workerPool);
        }
        registerListener(expiringService);
        registerListener(levelService);
        registerListener(itemReturnService);
        registerListener(notificationService);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")
                && configuration.generalSettings().hooks().placeholderapi()) {
            var placeholderService = new PlaceholderService(this, companyData, orderData);
            placeholderService.register();
            placeholderService.startRefreshing();
            registerListener(placeholderService);
        }
    }

    @Override
    public @NotNull List<DebugDataProvider> getDebugProviders() {
        return List.of(configuration, new CompaniesDebug(this, configuration, dataSource));
    }

    @Override
    public void onPluginDisable() {
        if (commands != null) commands.withdraw();
        SuggestionSources.clear();
        CompanyOrderSources.clear();
        MembershipSources.clear();
        CommandFailures.clear();
        if (updates != null) updates.stop();
        workerPool.shutdown();
        Threading.VIRTUAL.shutdown();
        awaitTermination(workerPool);
        awaitTermination(Threading.VIRTUAL);
        if (dataSource != null) dataSource.close();
    }

    private void awaitTermination(ExecutorService executor) {
        try {
            if (!executor.awaitTermination(10L, TimeUnit.SECONDS)) {
                getLogger().warning("Pending tasks did not finish in time and were cancelled.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
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
                throw new IllegalStateException(
                        "Unexpected value: " + configuration.databaseSettings().storageType());
            }
        }

        builder.setVersionTable("companies_db_version")
                .withClassLoader(this.getClass().getClassLoader())
                .setReplacements(new QueryReplacement(
                        "companies_schema", configuration.databaseSettings().schema()))
                .execute();

        initDataRepositories();
    }

    private void initDataRepositories() {
        var mapper = configuration.storageMapper();
        switch (configuration.databaseSettings().storageType()) {
            case SQLITE -> {
                companyData = new SqLiteCompanyData(workerPool);
                notificationData = new SqLiteNotificationData(workerPool);
                orderData = new SqLiteOrderData(workerPool, mapper);
                information = new InformationData(
                        new SqLiteInboxData(Threading.VIRTUAL),
                        new SqLiteStatsData(Threading.VIRTUAL),
                        new SqLiteHistoryData(mapper, Threading.VIRTUAL));
                nodeData = new SqLiteNodeData(configuration.nodeSettings());
                membershipData = new SqLiteMembershipData(workerPool);
            }
            case MARIADB -> {
                companyData = new MariaDbCompanyData(workerPool);
                notificationData = new MariaDbNotificationData(workerPool);
                orderData = new MariaDbOrderData(mapper);
                information = new InformationData(
                        new MariaDbInboxData(Threading.VIRTUAL),
                        new MariaDbStatsData(Threading.VIRTUAL),
                        new MariaDbHistoryData(mapper, Threading.VIRTUAL));
                nodeData = new MariaDbNodeData(configuration.nodeSettings());
                membershipData = new MariaDbMembershipData(workerPool);
            }
            case POSTGRES -> {
                companyData = new PostgresCompanyData(workerPool);
                notificationData = new PostgresNotificationData(workerPool);
                orderData = new PostgresOrderData(workerPool, mapper);
                information = new InformationData(
                        new PostgresInboxData(Threading.VIRTUAL),
                        new PostgresStatsData(Threading.VIRTUAL),
                        new PostgresHistoryData(mapper, Threading.VIRTUAL));
                nodeData = new PostgresNodeData(configuration.nodeSettings());
                membershipData = new PostgresMembershipData(workerPool);
            }
            default ->
                throw new IllegalStateException(
                        "Unexpected value: " + configuration.databaseSettings().storageType());
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
