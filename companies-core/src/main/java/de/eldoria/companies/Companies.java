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
import de.eldoria.companies.boards.SignBoards;
import de.eldoria.companies.boards.npc.NpcHooks;
import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.CommandRegistration;
import de.eldoria.companies.commands.suggest.AlertSuggestions;
import de.eldoria.companies.commands.suggest.CompanyOrderSources;
import de.eldoria.companies.commands.suggest.MembershipSources;
import de.eldoria.companies.commands.suggest.SeriesSources;
import de.eldoria.companies.commands.suggest.SuggestionSources;
import de.eldoria.companies.commands.suggest.TreasurySources;
import de.eldoria.companies.commands.support.CommandFailures;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.data.DataSourceFactory;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AMembershipData;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.companies.data.repository.ANodeMessageData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.EconomyData;
import de.eldoria.companies.data.repository.InformationData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbAlertData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbCompanyData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbHistoryData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbInboxData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbMembershipData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbNodeData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbNodeMessageData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbOrderData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbSeriesData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbStatsData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbTreasuryData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresAlertData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresCompanyData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresHistoryData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresInboxData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresMembershipData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresNodeData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresNodeMessageData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresOrderData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresSeriesData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresStatsData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresTreasuryData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteAlertData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteCompanyData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteHistoryData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteInboxData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteMembershipData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteNodeData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteNodeMessageData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteSeriesData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteStatsData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTreasuryData;
import de.eldoria.companies.data.wrapper.nodes.Node;
import de.eldoria.companies.debug.CompaniesDebug;
import de.eldoria.companies.integrations.discord.DiscordWebhook;
import de.eldoria.companies.menus.MenuService;
import de.eldoria.companies.nodes.DatabaseBus;
import de.eldoria.companies.nodes.NetworkPresence;
import de.eldoria.companies.nodes.NodeBus;
import de.eldoria.companies.nodes.NodeBuses;
import de.eldoria.companies.nodes.NodeCaches;
import de.eldoria.companies.nodes.NodeConnection;
import de.eldoria.companies.orders.CompanyOrderService;
import de.eldoria.companies.orders.DeliveryService;
import de.eldoria.companies.orders.HistoryRecorder;
import de.eldoria.companies.orders.OrderAcceptService;
import de.eldoria.companies.orders.OrderCompletionService;
import de.eldoria.companies.orders.OrderCreationService;
import de.eldoria.companies.orders.OrderDraftService;
import de.eldoria.companies.orders.OrderOutcomes;
import de.eldoria.companies.orders.SoloOrderService;
import de.eldoria.companies.placeholders.Placeholders;
import de.eldoria.companies.services.CompanyService;
import de.eldoria.companies.services.DisbandService;
import de.eldoria.companies.services.ExpiringService;
import de.eldoria.companies.services.HistoryRetention;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.services.LevelService;
import de.eldoria.companies.services.MemberService;
import de.eldoria.companies.services.MembershipService;
import de.eldoria.companies.services.ProfileService;
import de.eldoria.companies.services.RefreshService;
import de.eldoria.companies.services.SeriesService;
import de.eldoria.companies.services.TreasuryLogRetention;
import de.eldoria.companies.services.TreasuryService;
import de.eldoria.companies.services.alerts.AlertCompanies;
import de.eldoria.companies.services.alerts.AlertDigestJob;
import de.eldoria.companies.services.alerts.AlertMatcher;
import de.eldoria.companies.services.alerts.AlertNotices;
import de.eldoria.companies.services.notifications.InboxPoller;
import de.eldoria.companies.services.notifications.InboxRetention;
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
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@SuppressWarnings("InstanceVariableMayNotBeInitialized")
public class Companies extends EldoPlugin {
    private static final long ONLINE_TIMEOUT_SECONDS = 5L;
    private final Thread.UncaughtExceptionHandler exceptionHandler = (t, e) -> getLogger()
            .log(Level.SEVERE, "An uncaught exception occurred in " + t.getName() + "-" + t.threadId() + ".", e);
    private final ThreadGroup workerGroup = new ThreadGroup("Company Worker Pool");
    private final ScheduledExecutorService workerPool =
            Executors.newScheduledThreadPool(5, createThreadFactory(workerGroup));
    private final Configuration configuration;
    private HikariDataSource dataSource;
    private ACompanyData companyData;
    private AOrderData orderData;
    private InformationData information;
    private EconomyData economy;
    private ANodeData nodeData;
    private AMembershipData membershipData;
    private ANodeMessageData nodeMessages;
    private Updates updates;
    private CommandDependencies commands;
    private OrderDraftService drafts;
    private CompaniesApiImpl api;
    private DiscordWebhook discord;
    private NodeBus bus = new DatabaseBus();
    private final NetworkPresence presence = new NetworkPresence(Clock.systemUTC());
    private NodeCaches caches;
    private NodeConnection nodeConnection;

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
    }

    @Override
    public void onPluginEnable(boolean reload) {

        var localizer = Localizer.builder(this, "en_US")
                .setIncludedLocales("en_US", "de_DE", "fr_FR")
                .setUserLocale(p -> configuration.generalSettings().language())
                .build();

        StyledMessageSender.register(this, localizer, configuration::styleSettings);
        caches = new NodeCaches(getLogger());
        bus = NodeBuses.create(
                configuration.databaseSettings(),
                configuration.nodeSettings().nodeUid(),
                configuration.nodeSettings().nodeType() == NodeType.PRIMARY,
                nodeMessages,
                Threading.VIRTUAL,
                System::getenv,
                getLogger());
        registerCommands();
        api = CompaniesApiImpl.create(
                this,
                new CompaniesApiImpl.Repositories(companyData, orderData, information.stats(), economy.treasury()),
                Threading.VIRTUAL);
        api.register(this);
    }

    /**
     * Registers the command trees with the commands lifecycle event. The commands refuse until the post start hands
     * them the services built on the economy.
     */
    private void registerCommands() {
        var dependencies = new CommandDependencies(
                this, configuration, companyData, orderData, nodeData, membershipData, information, economy);
        commands = dependencies;
        dependencies.useNodes(bus, presence, caches);
        drafts = dependencies.drafts();
        CommandFailures.use((sender, failure) -> failure.sendTo(sender, MessageSender.getPluginMessageSender(this)));
        SuggestionSources.use(new SuggestionSources(
                companyData,
                orderData,
                dependencies.drafts(),
                configuration,
                ILocalizer.getPluginLocalizer(this),
                dependencies.npcBoards(),
                Threading.VIRTUAL));
        MembershipSources.use(new MembershipSources(companyData, membershipData, Threading.VIRTUAL));
        TreasurySources.use(new TreasurySources(companyData, economy.treasury(), Threading.VIRTUAL));
        SeriesSources.use(new SeriesSources(economy.series(), Threading.VIRTUAL));
        CompanyOrderSources.use(
                new CompanyOrderSources(companyData, orderData, dependencies.searches(), Threading.VIRTUAL));
        AlertSuggestions.use(new AlertSuggestions(companyData, economy.alerts(), Threading.VIRTUAL));
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

        var notificationService = NotificationService.create(
                this, information.inbox(), orderData, workerPool, configuration::notificationSettings);
        var treasuryService =
                TreasuryService.create(this, this.economy.treasury(), economy, configuration, notificationService);
        var disbandService = new DisbandService(
                this, companyData, orderData, itemReturnService, notificationService, treasuryService);
        var membershipService = MembershipService.create(
                this,
                companyData,
                membershipData,
                configuration::companySettings,
                notificationService,
                Threading.VIRTUAL);

        var historyRecorder = new HistoryRecorder(information.history(), Clock.systemDefaultZone());
        var outcomes = OrderOutcomes.create(this, orderData, companyData, historyRecorder, itemReturnService);
        var orderCompletion = OrderCompletionService.create(
                this, orderData, information.stats(), historyRecorder, economy, configuration, treasuryService);
        var soloOrders = SoloOrderService.create(this, orderData, companyData, outcomes, configuration);

        var orderCreation = new OrderCreationService(
                orderData,
                configuration::orderSetting,
                drafts.limits(),
                drafts::request,
                OrderCreationService.Wallet.of(economy),
                getServer().getPluginManager()::callEvent,
                Threading.mainThread(this),
                getLogger());

        var seriesService = SeriesService.create(
                this,
                this.economy.series(),
                orderData,
                orderCreation,
                drafts.limits(),
                economy::format,
                notificationService,
                () -> configuration.orderSetting().repeating());

        var profileService = new ProfileService(
                ProfileService.Store.of(companyData),
                () -> configuration.companySettings().profile(),
                getServer().getPluginManager()::callEvent,
                notificationService::send,
                Threading.mainThread(this));
        var orderAccept = new OrderAcceptService(
                orderData,
                configuration::companySettings,
                getServer().getPluginManager()::callEvent,
                uuid -> Optional.ofNullable(getServer().getPlayer(uuid)),
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
                    new DeliveryService(this, orderData, orderCompletion, itemReturnService, () -> configuration
                            .orderSetting()
                            .matching()),
                    new MenuService(
                            messageBlocker, () -> configuration.menuSettings().mode()),
                    membershipService,
                    orderCompletion,
                    orderCreation,
                    treasuryService,
                    seriesService,
                    soloOrders,
                    profileService,
                    MemberService.create(this, companyData, notificationService),
                    CompanyService.create(
                            this,
                            companyData,
                            treasuryService,
                            OrderCreationService.Wallet.of(economy),
                            configuration::companySettings),
                    orderAccept,
                    CompanyOrderService.create(this, companyData, orderData, outcomes)));
        }
        if (api != null) api.open(new CompaniesApiImpl.Writes(orderCreation, membershipService, itemReturnService));

        var expiringService = new ExpiringService(
                this, orderData, companyData, configuration, economy, notificationService, outcomes, soloOrders);
        var alertNotices = new AlertNotices(notificationService::send, economy::format);
        var alertCompanies = AlertCompanies.of(companyData, orderData);
        if (configuration.nodeSettings().nodeType() == NodeType.PRIMARY) {
            expiringService.start(workerPool);
            RefreshService.start(orderData, workerPool, () -> {
                if (nodeConnection != null) nodeConnection.pricesRefreshed();
            });
            membershipService.start(workerPool);
            seriesService.schedule(workerPool);
            new InboxRetention(information.inbox(), configuration::notificationSettings, getLogger()).start(workerPool);
            new HistoryRetention(
                            information.history(),
                            () -> configuration.generalSettings().historyDays(),
                            Clock.systemDefaultZone(),
                            getLogger())
                    .start(workerPool);
            new TreasuryLogRetention(
                            this.economy.treasury(),
                            () -> configuration.companySettings().treasury().logRetentionDays(),
                            Clock.systemDefaultZone(),
                            getLogger())
                    .start(workerPool);
            new AlertDigestJob(
                            this.economy.alerts(),
                            alertCompanies,
                            configuration::companySettings,
                            alertNotices,
                            Clock.systemDefaultZone(),
                            getLogger())
                    .start(workerPool);
        }
        var inboxPoller = new InboxPoller(information.inbox(), notificationService, getLogger());
        inboxPoller.start(workerPool, configuration::notificationSettings, bus::realtime);
        if (commands != null) commands.onReload(inboxPoller::reschedule);
        registerListener(expiringService);
        registerListener(levelService);
        registerListener(itemReturnService);
        registerListener(notificationService);
        if (commands != null && configuration.boardSettings().enabled()) {
            var signBoards = new SignBoards(
                    this,
                    commands.boards(),
                    configuration::boardSettings,
                    ILocalizer.getPluginLocalizer(this)::localize,
                    (sender, failure) -> failure.sendTo(sender, MessageSender.getPluginMessageSender(this)),
                    Clock.systemUTC());
            signBoards.start();
            caches.register(NodeCaches.BOARDS, key -> signBoards.refresher().markDirty());
        }
        if (commands != null) {
            commands.npcBoards()
                    .start(NpcHooks.create(
                            this,
                            name -> configuration.generalSettings().hooks().npc(name)));
        }

        discord = DiscordWebhook.start(
                this, configuration::discordSettings, economy, ILocalizer.getPluginLocalizer(this));
        registerListener(discord.listener());
        if (commands != null) commands.onReload(discord::restart);
        registerListener(seriesService);
        registerListener(new AlertMatcher(
                this.economy.alerts(),
                alertCompanies,
                configuration::companySettings,
                alertNotices,
                Clock.systemDefaultZone(),
                Threading.VIRTUAL,
                getLogger()));

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")
                && configuration.generalSettings().hooks().placeholderapi()) {
            var placeholders = Placeholders.create(
                    this,
                    configuration,
                    companyData,
                    orderData,
                    information.stats(),
                    this.economy.treasury(),
                    economy,
                    ILocalizer.getPluginLocalizer(this));
            placeholders.start();
            registerListener(placeholders.events());
            if (commands != null) commands.onReload(placeholders::reload);
            registerPlaceholderCaches(placeholders);
        }
        connectNodes(notificationService, inboxPoller);
    }

    /**
     * Lets other nodes drop the cached placeholder values of companies, players and rankings.
     *
     * @param placeholders the placeholders
     */
    private void registerPlaceholderCaches(Placeholders placeholders) {
        caches.register(NodeCaches.COMPANY, key -> key.map(Integer::parseInt)
                .ifPresentOrElse(placeholders::companyChanged, placeholders::refreshAll));
        caches.register(NodeCaches.PLAYER, key -> key.map(UUID::fromString)
                .ifPresentOrElse(placeholders::playerChanged, placeholders::refreshAll));
        caches.register(NodeCaches.RANKING, key -> placeholders.rankingChanged());
    }

    /**
     * Connects this node to the others over the node bus, see {@link NodeConnection}.
     *
     * @param notifications sends notifications
     * @param inboxPoller   shows notifications sent from other nodes
     */
    private void connectNodes(NotificationService notifications, InboxPoller inboxPoller) {
        if (commands == null) return;
        nodeConnection = NodeConnection.connect(
                new NodeConnection.Parts(
                        bus,
                        caches,
                        presence,
                        getLogger(),
                        configuration.nodeSettings().nodeType() == NodeType.PRIMARY,
                        notifications,
                        inboxPoller,
                        orderData,
                        NodeConnection.configSync(
                                configuration, nodeData, Threading.mainThread(this), commands::reloaded, getLogger()),
                        this::onlineHere,
                        () -> nodeData.nodes().stream().map(Node::uid).toList()),
                workerPool);
        registerListener(nodeConnection.signals());
    }

    private List<UUID> onlineHere() {
        return CompletableFuture.supplyAsync(
                        () -> getServer().getOnlinePlayers().stream()
                                .map(Player::getUniqueId)
                                .toList(),
                        Threading.mainThread(this))
                .orTimeout(ONLINE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .join();
    }

    @Override
    public @NotNull List<DebugDataProvider> getDebugProviders() {
        return List.of(configuration, new CompaniesDebug(this, configuration, dataSource, bus));
    }

    @Override
    public void onPluginDisable() {
        if (api != null) api.unregister(this);
        if (commands != null) {
            commands.npcBoards().stop();
            commands.withdraw();
        }
        SuggestionSources.clear();
        CompanyOrderSources.clear();
        TreasurySources.clear();
        AlertSuggestions.clear();
        MembershipSources.clear();
        SeriesSources.clear();
        CommandFailures.clear();
        if (updates != null) updates.stop();
        if (discord != null) discord.stop();
        if (nodeConnection != null) {
            nodeConnection.stop();
        } else {
            bus.close();
        }
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
                orderData = new SqLiteOrderData(workerPool, mapper);
                information = new InformationData(
                        new SqLiteInboxData(Threading.VIRTUAL),
                        new SqLiteStatsData(Threading.VIRTUAL),
                        new SqLiteHistoryData(mapper, Threading.VIRTUAL));
                economy = new EconomyData(
                        new SqLiteTreasuryData(Threading.VIRTUAL),
                        new SqLiteSeriesData(mapper, Threading.VIRTUAL),
                        new SqLiteAlertData(mapper, Threading.VIRTUAL));
                nodeData = new SqLiteNodeData(configuration.nodeSettings());
                membershipData = new SqLiteMembershipData(workerPool);
                nodeMessages = new SqLiteNodeMessageData();
            }
            case MARIADB -> {
                companyData = new MariaDbCompanyData(workerPool);
                orderData = new MariaDbOrderData(mapper);
                information = new InformationData(
                        new MariaDbInboxData(Threading.VIRTUAL),
                        new MariaDbStatsData(Threading.VIRTUAL),
                        new MariaDbHistoryData(mapper, Threading.VIRTUAL));
                economy = new EconomyData(
                        new MariaDbTreasuryData(Threading.VIRTUAL),
                        new MariaDbSeriesData(mapper, Threading.VIRTUAL),
                        new MariaDbAlertData(mapper, Threading.VIRTUAL));
                nodeData = new MariaDbNodeData(configuration.nodeSettings());
                membershipData = new MariaDbMembershipData(workerPool);
                nodeMessages = new MariaDbNodeMessageData();
            }
            case POSTGRES -> {
                companyData = new PostgresCompanyData(workerPool);
                orderData = new PostgresOrderData(workerPool, mapper);
                information = new InformationData(
                        new PostgresInboxData(Threading.VIRTUAL),
                        new PostgresStatsData(Threading.VIRTUAL),
                        new PostgresHistoryData(mapper, Threading.VIRTUAL));
                economy = new EconomyData(
                        new PostgresTreasuryData(Threading.VIRTUAL),
                        new PostgresSeriesData(mapper, Threading.VIRTUAL),
                        new PostgresAlertData(mapper, Threading.VIRTUAL));
                nodeData = new PostgresNodeData(configuration.nodeSettings());
                membershipData = new PostgresMembershipData(workerPool);
                nodeMessages = new PostgresNodeMessageData();
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
