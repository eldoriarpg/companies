/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies;

import com.zaxxer.hikari.HikariDataSource;
import de.chojo.sadu.core.updater.SqlVersion;
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
import de.eldoria.companies.commands.company.order.search.SearchExecutor;
import de.eldoria.companies.commands.companyadmin.NodeCommand;
import de.eldoria.companies.commands.suggest.AlertSuggestions;
import de.eldoria.companies.commands.suggest.CompanyOrderSources;
import de.eldoria.companies.commands.suggest.MembershipSources;
import de.eldoria.companies.commands.suggest.SavedItemSources;
import de.eldoria.companies.commands.suggest.SeriesSources;
import de.eldoria.companies.commands.suggest.SuggestionSources;
import de.eldoria.companies.commands.suggest.TreasurySources;
import de.eldoria.companies.commands.support.CommandFailures;
import de.eldoria.companies.configuration.ChangeTracker;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.dashboard.Dashboard;
import de.eldoria.companies.dashboard.DashboardModule;
import de.eldoria.companies.dashboard.KeyCommandFilter;
import de.eldoria.companies.data.DataSourceFactory;
import de.eldoria.companies.data.migration.UtcMigration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.ADashboardData;
import de.eldoria.companies.data.repository.AMembershipData;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.companies.data.repository.ANodeMessageData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.EconomyData;
import de.eldoria.companies.data.repository.InformationData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbAlertData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbCompanyData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbDashboardData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbHistoryData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbInboxData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbMembershipData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbNodeData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbNodeMessageData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbOrderData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbSavedItemData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbSeriesData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbStatsData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbTreasuryData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresAlertData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresCompanyData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresDashboardData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresHistoryData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresInboxData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresMembershipData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresNodeData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresNodeMessageData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresOrderData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresSavedItemData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresSeriesData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresStatsData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresTreasuryData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteAlertData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteCompanyData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteDashboardData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteHistoryData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteInboxData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteMembershipData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteNodeData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteNodeMessageData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteSavedItemData;
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
import de.eldoria.companies.nodes.NodeMessage;
import de.eldoria.companies.nodes.SettingsSync;
import de.eldoria.companies.orders.CompanyOrderService;
import de.eldoria.companies.orders.DeliveryService;
import de.eldoria.companies.orders.EconomyAccounts;
import de.eldoria.companies.orders.HistoryRecorder;
import de.eldoria.companies.orders.OrderAcceptService;
import de.eldoria.companies.orders.OrderCompletionService;
import de.eldoria.companies.orders.OrderCreationService;
import de.eldoria.companies.orders.OrderDraftService;
import de.eldoria.companies.orders.OrderOutcomes;
import de.eldoria.companies.orders.OrderReceiveService;
import de.eldoria.companies.orders.OrderRefunds;
import de.eldoria.companies.orders.OwnerOrderService;
import de.eldoria.companies.orders.SavedItemService;
import de.eldoria.companies.orders.SoloOrderService;
import de.eldoria.companies.orders.SystemOrders;
import de.eldoria.companies.orders.SystemOwner;
import de.eldoria.companies.orders.system.SystemFunding;
import de.eldoria.companies.orders.system.SystemOrderService;
import de.eldoria.companies.placeholders.Placeholders;
import de.eldoria.companies.services.AdminCompanyService;
import de.eldoria.companies.services.AdminGrantService;
import de.eldoria.companies.services.AdminOrderLookup;
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
import de.eldoria.companies.services.SetupReminder;
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
import de.eldoria.companies.util.Money;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
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
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@SuppressWarnings("InstanceVariableMayNotBeInitialized")
public class Companies extends EldoPlugin {
    private static final long ONLINE_TIMEOUT_SECONDS = 5L;
    private final Thread.UncaughtExceptionHandler exceptionHandler = (t, e) -> getLogger()
            .log(Level.SEVERE, "An uncaught exception occurred in " + t.getName() + "-" + t.threadId() + ".", e);
    private final ThreadGroup workerGroup = new ThreadGroup("Company Worker Pool");
    private static final long SYNC_APPLY_DELAY_TICKS = 40L;
    private @Nullable BukkitTask pendingApply;
    private @Nullable BukkitTask pendingChanges;
    private final ExecutorService levelSyncs =
            Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
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
    private ADashboardData dashboardData;
    private Dashboard dashboard;
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
        Times.useZone(() -> configuration.generalSettings().timezone().orElseGet(Times::machineZone));
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

        nodeData.restartPending(List.of());
        configuration.syncConfigurations(nodeData).join();
    }

    @Override
    public void onPluginEnable(boolean reload) {
        KeyCommandFilter.install(getLogger());

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
                getLogger());
        configuration.announceWith((path, revision) -> bus.publish(new NodeMessage.SettingsChanged(path, revision)));
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
        SavedItemSources.use(new SavedItemSources(economy.savedItems(), Threading.VIRTUAL));
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
        configuration.started();
        var economyProvider = getServer().getServicesManager().getRegistration(Economy.class);
        if (economyProvider == null) {
            throw new IllegalStateException(
                    "No Vault economy provider is registered. Install an economy plugin that supports Vault.");
        }
        var economy = economyProvider.getProvider();

        MessageBlocker messageBlocker;
        if (configuration.messageBlocking()) {
            messageBlocker =
                    MessageBlockerAPI.builder(this).executor(workerPool).build();
        } else {
            messageBlocker = MessageBlocker.dummy(this);
        }

        var levelService = new LevelService(this, configuration, companyData);

        updates = new Updates(this, configuration);
        updates.apply();
        if (commands != null) commands.onChange(updates::apply, ChangeTracker.UPDATES);

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

        var historyRecorder = new HistoryRecorder(information.history(), Times.UTC);
        var outcomes = OrderOutcomes.create(this, orderData, companyData, historyRecorder, itemReturnService);
        var orderCompletion = OrderCompletionService.create(
                this,
                orderData,
                information.stats(),
                historyRecorder,
                economy,
                configuration,
                treasuryService,
                outcomes);
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

        var systemFunding = new SystemFunding(
                new EconomyAccounts(getServer(), economy), orderData, Threading.mainThread(this), getLogger());
        var systemOrders = new SystemOrderService(
                configuration::systemOrderSettings,
                orderData,
                SystemOrderService.placement(
                        orderData,
                        new SystemOrders(orderCreation, orderData, getServer()::getOfflinePlayer, getLogger()),
                        outcomes),
                systemFunding,
                Times.UTC,
                () -> ThreadLocalRandom.current().nextDouble(),
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

        var deliveryService =
                new DeliveryService(this, orderData, orderCompletion, itemReturnService, () -> configuration
                        .orderSetting()
                        .matching());
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
        var ownerOrders = OwnerOrderService.create(
                this, orderData, outcomes, this.economy.series(), seriesService, economy, configuration);
        var search = new SearchExecutor(orderData, companyData, configuration, deliveryService::matcher);

        if (commands != null) {
            commands.provide(new CommandDependencies.Services(
                    economy,
                    messageBlocker,
                    itemReturnService,
                    levelService,
                    notificationService,
                    disbandService,
                    updates,
                    deliveryService,
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
                    CompanyOrderService.create(this, companyData, orderData, outcomes),
                    ownerOrders,
                    search,
                    new SavedItemService(this.economy.savedItems(), commands.drafts()::request, () -> configuration
                            .orderSetting()
                            .savedItems()),
                    new OrderReceiveService(
                            orderData, outcomes, itemReturnService, Threading.mainThread(this), getLogger()),
                    systemFunding));
        }
        if (api != null) api.open(new CompaniesApiImpl.Writes(orderCreation, membershipService, itemReturnService));

        var expiringService = new ExpiringService(
                this,
                orderData,
                companyData,
                configuration,
                notificationService,
                outcomes,
                soloOrders,
                OrderRefunds.of(this, economy, systemFunding));
        var alertNotices = new AlertNotices(notificationService::send, economy::format);
        var alertCompanies = AlertCompanies.of(companyData, orderData);
        if (configuration.nodeSettings().nodeType() == NodeType.PRIMARY) {
            expiringService.start(workerPool);
            RefreshService.start(orderData, workerPool, () -> {
                if (nodeConnection != null) nodeConnection.pricesRefreshed();
            });
            membershipService.start(workerPool);
            seriesService.schedule(workerPool);
            systemOrders.schedule(workerPool);
            new InboxRetention(information.inbox(), configuration::notificationSettings, getLogger()).start(workerPool);
            new HistoryRetention(
                            information.history(),
                            () -> configuration.generalSettings().historyDays(),
                            Times.UTC,
                            getLogger())
                    .start(workerPool);
            new TreasuryLogRetention(
                            this.economy.treasury(),
                            () -> configuration.companySettings().treasury().logRetentionDays(),
                            Times.UTC,
                            getLogger())
                    .start(workerPool);
            new AlertDigestJob(
                            this.economy.alerts(),
                            alertCompanies,
                            configuration::companySettings,
                            alertNotices,
                            Times.UTC,
                            getLogger())
                    .start(workerPool);
        }
        warnAboutTimezone();
        if (commands != null) commands.onChange(this::warnAboutTimezone, ChangeTracker.TIMEZONE);
        var inboxPoller = new InboxPoller(information.inbox(), notificationService, getLogger());
        inboxPoller.start(workerPool, configuration::notificationSettings, bus::realtime);
        if (commands != null) commands.onChange(inboxPoller::reschedule, ChangeTracker.NOTIFICATIONS);
        registerListener(expiringService);
        registerListener(levelService);
        registerListener(itemReturnService);
        registerListener(notificationService);
        var setupReminder =
                new SetupReminder(this, () -> nodeData, MessageSender.getPluginMessageSender(this), workerPool);
        registerListener(setupReminder);
        setupReminder.atStart();
        if (commands != null && configuration.boardsEnabled()) {
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
                    .start(NpcHooks.create(this, name -> configuration.hooks().npc(name)));
        }

        discord = DiscordWebhook.start(
                this, configuration::discordSettings, economy, ILocalizer.getPluginLocalizer(this));
        registerListener(discord.listener());
        if (commands != null) commands.onChange(discord::restart, ChangeTracker.DISCORD);
        registerListener(seriesService);
        registerListener(new AlertMatcher(
                this.economy.alerts(),
                alertCompanies,
                configuration::companySettings,
                alertNotices,
                Times.UTC,
                Threading.VIRTUAL,
                getLogger()));

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")
                && configuration.hooks().placeholderapi()) {
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
        startDashboard(search);
    }

    /**
     * Warns when {@code timezone} of {@code config.yml} names no zone; the machine's zone is used then.
     */
    private void warnAboutTimezone() {
        configuration.generalSettings().invalidTimezone().ifPresent(name -> getLogger()
                .warning("The timezone \"" + name + "\" in config.yml is no time zone; using " + Times.machineZone()
                        + " instead."));
    }

    /**
     * Starts the web dashboard of this node, which connects only while it is enabled. A new key stored on another node
     * makes it connect again.
     *
     * @param search the order search the dashboard pages through
     */
    private void startDashboard(SearchExecutor search) {
        if (commands == null || api == null) return;
        dashboard = DashboardModule.start(
                this,
                configuration,
                new DashboardModule.Data(
                        dashboardData,
                        api,
                        membershipData,
                        this.economy.alerts(),
                        information.inbox(),
                        this.economy.treasury(),
                        information.stats(),
                        information.history(),
                        search,
                        player -> companyData
                                .retrievePlayerCompanyProfile(getServer().getOfflinePlayer(player))
                                .asFuture(),
                        commands.services().orderAccept(),
                        commands.services().companyOrders(),
                        commands.services().ownerOrders(),
                        commands.services().notificationService().inbox(),
                        new DashboardModule.CompanyServices(
                                commands.services().members(),
                                commands.services().membershipService(),
                                commands.services().profiles(),
                                commands.services().companies(),
                                commands.services().disbandService(),
                                commands.services().treasury(),
                                () -> Money.currencyName(commands.services().economy())),
                        new DashboardModule.OrderServices(
                                commands.services().orderCreation(),
                                commands.services().series(),
                                commands.services().savedItems(),
                                commands.drafts()::request,
                                () -> configuration.userSettings().maxOrders()),
                        new DashboardModule.ItemServices(
                                commands.orderData(),
                                commands.services().deliveryService(),
                                commands.services().receive(),
                                commands.services().itemReturnService(),
                                player -> {
                                    CompanyOrderSources.invalidate(player.getUniqueId());
                                    SuggestionSources.current().ifPresent(sources -> sources.ownOrders()
                                            .invalidate(player.getUniqueId()));
                                    if (player.isOnline())
                                        commands.services().menus().close(player);
                                }),
                        new DashboardModule.AdminServices(
                                commands.adminOrders(),
                                new AdminOrderLookup(
                                        commands.orderData(),
                                        companyData,
                                        information.history(),
                                        configuration,
                                        uuid -> SystemOwner.name(uuid, id -> getServer()
                                                .getOfflinePlayer(id)
                                                .getName())),
                                commands.adminLog(),
                                name -> companyData.retrieveCompanyByName(name).asFuture(),
                                new AdminCompanyService(companyData, commands.companyChanges()),
                                companyData,
                                new AdminGrantService(
                                        AdminGrantService.Bank.of(
                                                commands.services().economy()),
                                        commands.services().itemReturnService(),
                                        commands.services().notificationService()::send),
                                commands.levels(),
                                commands.services().levelService()::updateAllCompanies,
                                commands.boards(),
                                commands.npcBoards(),
                                () -> nodeData,
                                node -> NodeCommand.bus(commands, nodeData, node),
                                this::syncConfigurationFromDashboard,
                                this::applyChangesFromDashboard)),
                () -> bus.publish(new NodeMessage.CacheInvalidate(NodeCaches.DASHBOARD, NodeCaches.DASHBOARD_KEY)),
                workerPool);
        var started = dashboard;
        caches.register(NodeCaches.DASHBOARD, key -> {
            if (key.filter(NodeCaches.DASHBOARD_KEY::equals).isPresent()) started.keyChangedElsewhere();
        });
        commands.useDashboard(started);
        commands.onChange(started::restartLater, ChangeTracker.DASHBOARD);
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
     * Does what {@code /companyadmin syncconfiguration} does on the primary, for the dashboard, which asks only the
     * primary: stores the changed files as new revisions, which tells the secondaries, and applies the files a few
     * seconds later on the main thread, since applying them reconnects the dashboard and the answer has to reach the
     * gateway first. Syncs in quick succession share one later apply.
     *
     * @param admin who synced, stored with a new revision
     */
    private synchronized void syncConfigurationFromDashboard(String admin) {
        configuration.syncConfigurations(nodeData, admin).join();
        if (pendingApply != null) pendingApply.cancel();
        pendingApply = getServer().getScheduler().runTaskLater(this, commands::reloaded, SYNC_APPLY_DELAY_TICKS);
    }

    /**
     * Applies the shared settings the dashboard changed on this node a few seconds later on the main thread, since
     * applying them can reconnect the dashboard and the answer has to reach the gateway first. Saves in quick succession
     * share one later apply.
     */
    private synchronized void applyChangesFromDashboard() {
        if (pendingChanges != null) pendingChanges.cancel();
        pendingChanges = getServer().getScheduler().runTaskLater(this, commands::applyChanges, SYNC_APPLY_DELAY_TICKS);
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
                        new SettingsSync(
                                configuration,
                                nodeData,
                                levelSyncs,
                                Threading.mainThread(this),
                                commands::applyChanges,
                                bus::realtime,
                                getLogger()),
                        nodeData::settingsRevisions,
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
        if (dashboard != null) dashboard.stop();
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
        SavedItemSources.clear();
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
        levelSyncs.shutdown();
        if (commands != null) {
            awaitTermination(commands.stopLevelShares());
            awaitTermination(commands.stopRestartWrites());
        }
        awaitTermination(levelSyncs);
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

        var settings = configuration.databaseSettings();
        var utc = new UtcMigration(
                UtcMigration.Dialect.of(settings.storageType().name()), Times.machineZone(), settings.schema());
        builder.setVersionTable("companies_db_version")
                .withClassLoader(this.getClass().getClassLoader())
                .setReplacements(new QueryReplacement("companies_schema", settings.schema()))
                .preUpdateHook(new SqlVersion(1, 14), connection -> getLogger()
                        .info("Converted " + utc.run(connection) + " rows of stored times from " + Times.machineZone()
                                + " to UTC."))
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
                        new SqLiteAlertData(mapper, Threading.VIRTUAL),
                        new SqLiteSavedItemData(mapper, Threading.VIRTUAL));
                nodeData = new SqLiteNodeData(configuration.nodeSettings());
                membershipData = new SqLiteMembershipData(workerPool);
                nodeMessages = new SqLiteNodeMessageData();
                dashboardData = new SqLiteDashboardData();
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
                        new MariaDbAlertData(mapper, Threading.VIRTUAL),
                        new MariaDbSavedItemData(mapper, Threading.VIRTUAL));
                nodeData = new MariaDbNodeData(configuration.nodeSettings());
                membershipData = new MariaDbMembershipData(workerPool);
                nodeMessages = new MariaDbNodeMessageData();
                dashboardData = new MariaDbDashboardData();
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
                        new PostgresAlertData(mapper, Threading.VIRTUAL),
                        new PostgresSavedItemData(mapper, Threading.VIRTUAL));
                nodeData = new PostgresNodeData(configuration.nodeSettings());
                membershipData = new PostgresMembershipData(workerPool);
                nodeMessages = new PostgresNodeMessageData();
                dashboardData = new PostgresDashboardData();
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
