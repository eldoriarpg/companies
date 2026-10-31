/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard;

import de.eldoria.companies.api.CompaniesApiImpl;
import de.eldoria.companies.boards.Boards;
import de.eldoria.companies.boards.npc.FakeHook;
import de.eldoria.companies.boards.npc.NpcBoards;
import de.eldoria.companies.boards.npc.NpcHooks;
import de.eldoria.companies.commands.company.order.search.SearchExecutor;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.components.order.OrderSource;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.BoardBindings;
import de.eldoria.companies.configuration.elements.BoardSettings;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.ItemMatchSettings;
import de.eldoria.companies.configuration.elements.NodeSettings;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.configuration.elements.NotificationSettings;
import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.configuration.elements.UserSettings;
import de.eldoria.companies.configuration.elements.companysettings.ProfileSettings;
import de.eldoria.companies.configuration.elements.jobs.JobSettings;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.configuration.elements.systemorders.SystemOrderSettings;
import de.eldoria.companies.dashboard.actions.AdminEventWrites;
import de.eldoria.companies.dashboard.actions.AdminJobWrites;
import de.eldoria.companies.dashboard.actions.AdminLevelWrites;
import de.eldoria.companies.dashboard.actions.AdminOfferWrites;
import de.eldoria.companies.dashboard.actions.AdminOrderWrites;
import de.eldoria.companies.dashboard.actions.AdminServerWrites;
import de.eldoria.companies.dashboard.actions.AdminSettingsWrites;
import de.eldoria.companies.dashboard.actions.AdminSupportWrites;
import de.eldoria.companies.dashboard.actions.AdminSystemOrderWrites;
import de.eldoria.companies.dashboard.actions.CompanyWrites;
import de.eldoria.companies.dashboard.actions.DashboardWrites;
import de.eldoria.companies.dashboard.actions.EventWrites;
import de.eldoria.companies.dashboard.actions.ItemWrites;
import de.eldoria.companies.dashboard.actions.JobWrites;
import de.eldoria.companies.dashboard.actions.OrderCreateWrites;
import de.eldoria.companies.dashboard.market.MarketHistory;
import de.eldoria.companies.dashboard.views.DashboardReads;
import de.eldoria.companies.dashboard.views.OrderCatalogue;
import de.eldoria.companies.dashboard.views.Views;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteAdapter;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteAlertData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteAreaData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteCompanyData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteDashboardData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteHistoryData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteHomeData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteInboxData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteJobData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteMembershipData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteNodeData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOfferData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteSavedItemData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteSeriesData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteServerEventData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteStatsData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTreasuryData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferLine;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.data.wrapper.series.OrderSeries;
import de.eldoria.companies.events.EventRecorder;
import de.eldoria.companies.jobs.Blueprints;
import de.eldoria.companies.jobs.BuildGhosts;
import de.eldoria.companies.jobs.JobAccess;
import de.eldoria.companies.jobs.JobProgress;
import de.eldoria.companies.jobs.JobService;
import de.eldoria.companies.jobs.JobServices;
import de.eldoria.companies.jobs.hooks.JobHooks;
import de.eldoria.companies.orders.CompanyOrderService;
import de.eldoria.companies.orders.DeliveryResult;
import de.eldoria.companies.orders.HistoryRecorder;
import de.eldoria.companies.orders.ItemMatcher;
import de.eldoria.companies.orders.OrderAcceptService;
import de.eldoria.companies.orders.OrderCompletionService;
import de.eldoria.companies.orders.OrderCreationService;
import de.eldoria.companies.orders.OrderDraftService;
import de.eldoria.companies.orders.OrderLimits;
import de.eldoria.companies.orders.OrderOutcomes;
import de.eldoria.companies.orders.OrderReceiveService;
import de.eldoria.companies.orders.OwnerOrderService;
import de.eldoria.companies.orders.SavedItemService;
import de.eldoria.companies.orders.SystemOwner;
import de.eldoria.companies.orders.offers.ContestService;
import de.eldoria.companies.orders.system.SystemOrderService;
import de.eldoria.companies.services.AdminCompanyService;
import de.eldoria.companies.services.AdminGrantService;
import de.eldoria.companies.services.AdminOrderLookup;
import de.eldoria.companies.services.AdminOrderService;
import de.eldoria.companies.services.CompanyChanges;
import de.eldoria.companies.services.CompanyService;
import de.eldoria.companies.services.DisbandService;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.services.LevelAdminService;
import de.eldoria.companies.services.MemberService;
import de.eldoria.companies.services.MembershipService;
import de.eldoria.companies.services.ProfileService;
import de.eldoria.companies.services.SeriesService;
import de.eldoria.companies.services.TreasuryService;
import de.eldoria.companies.services.events.ServerEventService;
import de.eldoria.companies.services.notifications.Inbox;
import de.eldoria.companies.services.notifications.NotificationService;
import de.eldoria.companies.util.AdminLog;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.companies.util.TestExecutors;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import de.eldoria.companies.web.api.session.PlayerRef;
import de.eldoria.eldoutilities.localization.ILocalizer;
import dev.chojo.ocular.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * A server with a SQLite database, companies, orders and the dashboard's views on top, for dashboard tests.
 */
public final class DashboardWorld implements AutoCloseable {
    public final ServerMock server;
    public final Plugin plugin;
    public final ItemReturnService itemReturns;
    public final RecordedDeliveries deliveries = new RecordedDeliveries();
    public final List<UUID> moved = new CopyOnWriteArrayList<>();
    public final List<String> adminActions = new CopyOnWriteArrayList<>();
    public volatile NodeType nodeType = NodeType.PRIMARY;
    public final AtomicInteger levelShares = new AtomicInteger();
    public final AtomicInteger recalculations = new AtomicInteger();
    public final AtomicInteger configSyncs = new AtomicInteger();
    public volatile boolean failSync;
    public volatile boolean companyUnreadable;
    public final BoardBindings boardBindings = new BoardBindings();
    public final FakeHook citizens = new FakeHook("Citizens", Set.of("4", "5"));
    public SqLiteNodeData nodeData;
    public Configuration settings;
    public final SystemOrderSettings systemOrders = new SystemOrderSettings();
    public final AtomicInteger settingsApplied = new AtomicInteger();
    private final Path directory;
    public AdminLog adminLog;
    public final ExecutorService executor = Executors.newFixedThreadPool(4);
    public final Map<UUID, String> names = new ConcurrentHashMap<>();
    public final SqLiteCompanyData companies;
    public final SqLiteOrderData orders;
    public final SqLiteTreasuryData treasury;
    public final SqLiteMembershipData memberships;
    public final SqLiteInboxData inbox;
    public final SqLiteDashboardData dashboard;
    public final SqLiteAreaData areas;
    public final SqLiteHomeData homes;
    public final CompaniesApiImpl api;
    public final Views views;
    public final DashboardReads reads;
    public final DashboardWrites writes;
    public final TreasuryService treasuryService;
    public final SqLiteHistoryData history;
    public final SavedItemService savedItems;
    public final OrderCatalogue catalogue;
    public final OrderCreationService creation;
    public final SqLiteSeriesData seriesData;
    public final UserSettings userSettings = new UserSettings();
    public volatile int orderHistoryDays = 90;
    public final MarketHistory market;
    public final EventRecorder events = new EventRecorder();
    public final Map<UUID, Double> wallets = new ConcurrentHashMap<>();
    public final JobSettings jobSettings = new JobSettings();
    public SqLiteJobData jobs;
    public JobServices jobServices;
    public final List<Integer> changedOrders = new CopyOnWriteArrayList<>();
    public final List<UUID> changedInboxes = new CopyOnWriteArrayList<>();
    public final CompanySettings companySettings = new CompanySettings();
    public final OrderSettings orderSettings = new OrderSettings();
    public final SqLiteOfferData offers;
    public final SqLiteServerEventData serverEvents;
    public final ServerEventService eventService;
    public final ContestService contests;
    public final AtomicInteger offerChecks = new AtomicInteger();
    public final Map<String, Double> accounts = new ConcurrentHashMap<>();
    public volatile int contestSlots = 5;

    private DashboardWorld(Path directory) throws Exception {
        this.directory = directory;
        server = MockBukkit.mock();
        SqLiteTestDatabase.create(directory);
        companies = new SqLiteCompanyData(executor);
        orders = new SqLiteOrderData(null, SqLiteTestDatabase.mapper());
        treasury = new SqLiteTreasuryData(executor);
        memberships = new SqLiteMembershipData(executor);
        inbox = new SqLiteInboxData(executor);
        dashboard = new SqLiteDashboardData();
        offers = new SqLiteOfferData(SqLiteTestDatabase.mapper());
        serverEvents = new SqLiteServerEventData();
        eventService = new ServerEventService(
                serverEvents,
                material -> material.toUpperCase(Locale.ROOT),
                message -> {},
                Times.UTC,
                Logger.getAnonymousLogger());
        var stats = new SqLiteStatsData(executor);
        areas = new SqLiteAreaData();
        homes = new SqLiteHomeData();
        var alerts = new SqLiteAlertData(SqLiteTestDatabase.mapper(), executor);
        api = new CompaniesApiImpl(
                new CompaniesApiImpl.Repositories(companies, orders, stats, treasury, serverEvents, areas, homes),
                new CompaniesApiImpl.Server(
                        server::getOfflinePlayer, failure -> Component.text(failure.localeKey()), Times::now),
                executor);
        views = new Views(new Views.Sources(
                api,
                stats,
                memberships,
                alerts,
                inbox,
                () -> companySettings,
                () -> orderSettings,
                this::name,
                ZoneOffset.UTC,
                Clock.systemUTC(),
                systemOrders::markSystemOrders,
                offers::marks));
        var configuration = new Configuration(MockBukkit.createMockPlugin()) {
            @Override
            public CompanySettings companySettings() {
                return companySettings;
            }

            @Override
            public SystemOrderSettings systemOrderSettings() {
                return systemOrders;
            }
        };
        history = new SqLiteHistoryData(SqLiteTestDatabase.mapper(), executor);
        market = new MarketHistory(new MarketHistory.Sources(
                dashboard,
                history,
                orderSettings::market,
                () -> orderHistoryDays,
                Times::zone,
                Clock.systemUTC(),
                Logger.getAnonymousLogger()));
        Function<ItemStack, Optional<OrderDraftService.RequestedItem>> requests =
                item -> item.getType() == Material.BEDROCK
                        ? Optional.empty()
                        : Optional.of(new OrderDraftService.RequestedItem(item.asOne(), item.hasItemMeta()));
        savedItems = new SavedItemService(
                new SqLiteSavedItemData(SqLiteTestDatabase.mapper(), executor), requests, orderSettings::savedItems);
        catalogue = new OrderCatalogue(requests, savedItems, history);
        creation = new OrderCreationService(
                orders,
                () -> orderSettings,
                new OrderLimits(orders, () -> userSettings),
                requests,
                new Wallet(),
                events,
                Runnable::run,
                Logger.getAnonymousLogger());
        seriesData = new SqLiteSeriesData(SqLiteTestDatabase.mapper(), executor);
        var series = new SeriesService(
                seriesData,
                (owner, draft) -> creation.create(offline(owner), draft, OrderSource.REPEATING, true, false),
                id -> orders.retrievePlayerOrderCount(offline(id)).asFuture(),
                player -> 5,
                new SilentNotices(),
                orderSettings::repeating,
                Clock.systemUTC(),
                Logger.getAnonymousLogger());
        var search = new SearchExecutor(
                orders,
                companies,
                configuration,
                () -> ItemMatcher.of(new ItemMatchSettings()),
                SqLiteTestDatabase::player,
                offers::marks);
        reads = new DashboardReads(
                views,
                new DashboardReads.Sources(
                        search::search,
                        inbox,
                        memberships,
                        treasury,
                        dashboard::market,
                        orderSettings::market,
                        market::changes,
                        (material, days) -> market.points(
                                material, days, orderSettings.market().minOrders()),
                        market::today,
                        catalogue::catalogue,
                        catalogue::known,
                        entry -> entry.type().name(),
                        key -> "text of " + key,
                        this::name,
                        Clock.systemUTC()));
        var plugin = MockBukkit.createMockPlugin();
        this.plugin = plugin;
        StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        itemReturns = new ItemReturnService(plugin, orders);
        var outcomes = new OrderOutcomes(
                orders,
                new HistoryRecorder(history, Clock.systemUTC()),
                itemReturns,
                events,
                ISimpleCompany::forId,
                Logger.getAnonymousLogger(),
                executor);
        var changes = new CompanyChanges(companies, this::offline, events, Logger.getAnonymousLogger(), executor);
        treasuryService = new TreasuryService(
                treasury,
                new TreasuryWallet(),
                companySettings::treasury,
                (recipient, type, args) -> {},
                events,
                Runnable::run,
                Clock.systemDefaultZone(),
                Logger.getAnonymousLogger());
        var disband = new DisbandService(
                plugin,
                companies,
                orders,
                itemReturns,
                new NotificationService(
                        plugin,
                        inbox,
                        orders,
                        null,
                        NotificationSettings::new,
                        (player, type, args) -> {},
                        Runnable::run),
                treasuryService,
                ignored -> 0L);
        var company = new CompanyWrites(new CompanyWrites.Sources(
                player -> companies
                        .retrievePlayerCompanyProfile(SqLiteTestDatabase.player(player))
                        .asFuture(),
                this::offline,
                new MemberService(companies, changes, (recipient, type, args) -> {}, events, this::offline, executor),
                new MembershipService(
                        companies,
                        memberships,
                        () -> companySettings,
                        (recipient, type, args) -> {},
                        new MembershipEvents(),
                        this::offline,
                        Times::now,
                        executor,
                        Logger.getAnonymousLogger()),
                new ProfileService(
                        ProfileService.Store.of(companies),
                        ProfileSettings::new,
                        events,
                        (recipient, type, args) -> {},
                        Runnable::run),
                new CompanyService(
                        companies,
                        changes,
                        treasuryService,
                        new Wallet(),
                        () -> companySettings,
                        this::offline,
                        uuid -> Optional.empty(),
                        Runnable::run,
                        executor,
                        Logger.getAnonymousLogger()),
                disband,
                treasuryService,
                () -> "coins",
                failure -> "text of " + failure.localeKey(),
                text -> "text of " + text.build()));
        var adminConfiguration = new Configuration(plugin) {
            @Override
            public CompanySettings companySettings() {
                return companySettings;
            }

            @Override
            public OrderSettings orderSetting() {
                return orderSettings;
            }

            @Override
            public NodeSettings nodeSettings() {
                return new NodeSettings() {
                    @Override
                    public NodeType nodeType() {
                        return nodeType;
                    }
                };
            }

            @Override
            public void save() {}

            @Override
            public boolean unreadable(Key<?> key) {
                return key == COMPANY_SETTINGS && companyUnreadable;
            }
        };
        var adminOrderService = new AdminOrderService(
                orders,
                id -> companies.retrieveCompanyById(id).asFuture().join().flatMap(found -> companies
                        .retrieveCompanyProfile(found)
                        .asFuture()
                        .join()),
                new OrderCompletionService(
                        plugin,
                        orders,
                        stats,
                        new HistoryRecorder(history, Clock.systemUTC()),
                        new OrderCompletionService.Treasury() {
                            @Override
                            public void pay(OfflinePlayer worker, double amount) {
                                wallets.merge(worker.getUniqueId(), amount, Double::sum);
                            }

                            @Override
                            public void collect(double amount) {}
                        },
                        level -> 0.0,
                        () -> 5.0,
                        Clock.systemUTC(),
                        executor,
                        order -> CompletableFuture.completedFuture(false)),
                new HistoryRecorder(history, Clock.systemUTC()),
                new AdminOrderService.Funds() {
                    @Override
                    public void refund(SimpleOrder order, double amount) {
                        wallets.merge(order.owner(), amount, Double::sum);
                    }

                    @Override
                    public void collect(double amount) {}
                },
                new AdminOrderService.Notices() {
                    @Override
                    public void ownerReopened(SimpleOrder order) {}

                    @Override
                    public void companyUnclaimed(CompanyProfile company, SimpleOrder order) {}

                    @Override
                    public void ownerCancelled(SimpleOrder order, double refund) {}

                    @Override
                    public void companyCancelled(CompanyProfile company, SimpleOrder order) {}

                    @Override
                    public void ownerRefunded(SimpleOrder order, double refund) {}

                    @Override
                    public void soloReleased(UUID worker, SimpleOrder order) {}
                },
                itemReturns,
                events,
                order -> {},
                Logger.getAnonymousLogger(),
                executor);
        var adminLogger = Logger.getAnonymousLogger();
        adminLogger.setUseParentHandlers(false);
        adminLogger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                adminActions.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        adminLog = new AdminLog(adminLogger);
        contests = new ContestService(
                offers,
                orders,
                new ContestService.Payments() {
                    @Override
                    public double taxPercent(CompanyProfile company) {
                        return 0;
                    }

                    @Override
                    public int share(CompanyProfile company) {
                        return 0;
                    }

                    @Override
                    public void pay(UUID worker, double amount) {
                        wallets.merge(worker, amount, Double::sum);
                    }

                    @Override
                    public void collect(double amount) {}

                    @Override
                    public void keep(CompanyProfile company, int offer, long cents) {}
                },
                profile -> contestSlots,
                Clock.systemUTC(),
                Logger.getAnonymousLogger());
        writes = new DashboardWrites(
                new DashboardWrites.Sources(
                        player -> companies
                                .retrievePlayerCompanyProfile(SqLiteTestDatabase.player(player))
                                .asFuture(),
                        new OrderAcceptService(
                                orders, () -> companySettings, events, uuid -> Optional.empty(), executor),
                        new CompanyOrderService(
                                companies, orders, outcomes, events, uuid -> {}, SqLiteTestDatabase::player, executor),
                        new OwnerOrderService(
                                orders,
                                outcomes,
                                id -> CompletableFuture.completedFuture(Optional.empty()),
                                order -> CompletableFuture.completedFuture(false),
                                () -> orderSettings,
                                new Wallet(),
                                amount -> {},
                                uuid -> {},
                                SqLiteTestDatabase::player,
                                Runnable::run,
                                executor,
                                Logger.getAnonymousLogger()),
                        new Inbox(inbox, NotificationSettings::new),
                        failure -> "text of " + failure.localeKey(),
                        order -> changedOrders.add(order.id()),
                        changedInboxes::add,
                        Clock.systemUTC()),
                company,
                new ItemWrites(new ItemWrites.Sources(
                        uuid -> Optional.ofNullable(server.getPlayer(uuid)),
                        Threading.mainThread(plugin),
                        player -> companies
                                .retrievePlayerCompanyProfile(SqLiteTestDatabase.player(player))
                                .asFuture(),
                        orders,
                        deliveries,
                        new OrderReceiveService(
                                orders,
                                outcomes,
                                itemReturns,
                                Threading.mainThread(plugin),
                                Logger.getAnonymousLogger()),
                        itemReturns,
                        failure -> "text of " + failure.localeKey(),
                        player -> moved.add(player.getUniqueId()))),
                new OrderCreateWrites(new OrderCreateWrites.Sources(
                        this::offline,
                        creation,
                        series,
                        catalogue,
                        requests,
                        () -> userSettings.maxOrders(),
                        failure -> "text of " + failure.localeKey())),
                new AdminOrderWrites(new AdminOrderWrites.Sources(
                        new AdminOrderLookup(orders, companies, history, adminConfiguration, this::name),
                        adminOrderService,
                        name -> names.entrySet().stream()
                                .filter(entry -> entry.getValue().equalsIgnoreCase(name))
                                .findFirst()
                                .map(entry -> new PlayerRef(entry.getKey(), entry.getValue())),
                        name -> companies.retrieveCompanyByName(name).asFuture(),
                        this::name,
                        views,
                        adminLog,
                        failure -> "text of " + failure.localeKey())),
                new AdminSupportWrites(new AdminSupportWrites.Sources(
                        name -> names.entrySet().stream()
                                .filter(entry -> entry.getValue().equalsIgnoreCase(name))
                                .findFirst()
                                .map(entry -> new PlayerRef(entry.getKey(), entry.getValue())),
                        this::offline,
                        this::name,
                        new AdminCompanyService(companies, changes),
                        companies,
                        orders,
                        inbox,
                        entry -> entry.type().name(),
                        new AdminGrantService(
                                new AdminGrantService.Bank() {
                                    @Override
                                    public String deposit(OfflinePlayer player, double amount) {
                                        wallets.merge(player.getUniqueId(), amount, Double::sum);
                                        return null;
                                    }

                                    @Override
                                    public String format(double amount) {
                                        return String.format(java.util.Locale.ROOT, "%.2f", amount);
                                    }
                                },
                                itemReturns,
                                (recipient, type, args) -> {}),
                        treasuryService,
                        treasury,
                        disband,
                        Runnable::run,
                        views,
                        adminLog,
                        failure -> "text of " + failure.localeKey())),
                new AdminLevelWrites(new AdminLevelWrites.Sources(
                        new LevelAdminService(
                                adminConfiguration,
                                admin -> levelShares::incrementAndGet,
                                adminLog,
                                Runnable::run,
                                Logger.getAnonymousLogger()),
                        done -> {
                            recalculations.incrementAndGet();
                            done.run();
                        },
                        Runnable::run,
                        adminLog,
                        failure -> "text of " + failure.localeKey())),
                adminServers(orders),
                new AdminSettingsWrites(new AdminSettingsWrites.Sources(
                        settings,
                        () -> nodeData,
                        settingsApplied::incrementAndGet,
                        adminLog,
                        Logger.getAnonymousLogger(),
                        views::instant,
                        failure -> "text of " + failure.localeKey())),
                new AdminSystemOrderWrites(new AdminSystemOrderWrites.Sources(
                        orders,
                        SqLiteTestDatabase.player(SystemOwner.ID),
                        () -> systemOrders,
                        views::instant,
                        Times::now,
                        adminLog,
                        settings,
                        () -> nodeData,
                        settingsApplied::incrementAndGet,
                        material -> !material.equals("NOT_AN_ITEM"),
                        Logger.getAnonymousLogger(),
                        failure -> "text of " + failure.localeKey())),
                new EventWrites(new EventWrites.Sources(
                        offers,
                        serverEvents,
                        contests,
                        player -> companies
                                .retrievePlayerCompanyProfile(SqLiteTestDatabase.player(player))
                                .asFuture(),
                        views::instant,
                        Times::now,
                        failure -> "text of " + failure.localeKey())),
                new AdminOfferWrites(new AdminOfferWrites.Sources(
                        offers,
                        new SystemOrderService.Funding() {
                            @Override
                            public boolean withdraw(String account, double amount) {
                                accounts.merge(account, -amount, Double::sum);
                                return true;
                            }

                            @Override
                            public void deposit(String account, double amount) {
                                accounts.merge(account, amount, Double::sum);
                            }
                        },
                        offerChecks::incrementAndGet,
                        this::name,
                        views::instant,
                        Times::now,
                        adminLog,
                        Logger.getAnonymousLogger(),
                        failure -> "text of " + failure.localeKey())),
                new AdminEventWrites(new AdminEventWrites.Sources(
                        serverEvents,
                        offers,
                        eventService,
                        () -> ZoneOffset.UTC,
                        () -> "collector",
                        views::instant,
                        Times::now,
                        adminLog,
                        failure -> "text of " + failure.localeKey())),
                new JobWrites(new JobWrites.Sources(
                        () -> jobServices,
                        player -> companies
                                .retrievePlayerCompanyProfile(SqLiteTestDatabase.player(player))
                                .asFuture(),
                        () -> jobSettings,
                        views::instant,
                        Times::now,
                        failure -> "text of " + failure.localeKey())),
                new AdminJobWrites(new AdminJobWrites.Sources(
                        () -> jobServices,
                        this::name,
                        id -> profile(id).map(CompanyProfile::name),
                        views::instant,
                        adminLog,
                        failure -> "text of " + failure.localeKey())));
        jobServices = jobServices();
    }

    private Optional<CompanyProfile> profile(int id) {
        var simple = companies.retrieveCompanyById(id).asFuture().join();
        if (simple == null || simple.isEmpty()) return Optional.empty();
        var profile = companies.retrieveCompanyProfile(simple.get()).asFuture().join();
        return profile == null ? Optional.empty() : profile;
    }

    private JobServices jobServices() {
        jobs = new SqLiteJobData();
        var logger = Logger.getAnonymousLogger();
        var service = new JobService(
                jobs,
                orders,
                new JobService.Money() {
                    @Override
                    public boolean withdraw(UUID player, double amount) {
                        if (wallets.getOrDefault(player, 0.0) < amount) return false;
                        wallets.merge(player, -amount, Double::sum);
                        return true;
                    }

                    @Override
                    public void deposit(UUID player, double amount) {
                        wallets.merge(player, amount, Double::sum);
                    }

                    @Override
                    public void collect(double amount) {}

                    @Override
                    public double taxPercent(CompanyProfile company) {
                        return 0;
                    }

                    @Override
                    public int share(CompanyProfile company) {
                        return 0;
                    }

                    @Override
                    public void keep(CompanyProfile company, int job, long cents) {}
                },
                new JobService.Notices() {
                    @Override
                    public void owner(UUID owner, JobService.Notice notice, Job job, @Nullable Object detail) {}

                    @Override
                    public void paid(CompanyProfile company, Job job, double paid) {}

                    @Override
                    public void overdue(int company, Job job) {}

                    @Override
                    public void landLost(int company, Job job) {}

                    @Override
                    public void rejected(int company, Job job, String reason) {}

                    @Override
                    public void disputed(int company, Job job, String reason) {}

                    @Override
                    public void settled(int company, Job job, double paid) {}

                    @Override
                    public void withdrawn(int company, Job job) {}
                },
                (company, overdue) -> {},
                this::profile,
                () -> jobSettings,
                price -> 0,
                company -> 5,
                Clock.systemUTC(),
                logger);
        var hooks = new JobHooks(name -> false, className -> new Object(), logger);
        var blueprints = new Blueprints(jobs, logger);
        var node = new UUID(0, 1);
        return new JobServices(
                jobs,
                service,
                hooks,
                new JobAccess(
                        jobs,
                        company -> Set.of(),
                        hooks,
                        node,
                        Runnable::run,
                        job -> {},
                        (job, company, worker, items) -> {},
                        blueprints,
                        logger),
                new JobProgress(plugin, node, blueprints, () -> 95),
                node,
                blueprints,
                new BuildGhosts(plugin));
    }

    private AdminServerWrites adminServers(SqLiteOrderData orders) {
        nodeData = new SqLiteNodeData(new NodeSettings() {
            @Override
            public NodeType nodeType() {
                return nodeType;
            }
        });
        nodeData.updateNode(plugin);
        settings = new Configuration(configPlugin(directory.resolve("settings")));
        var boardSettings = new BoardSettings();
        var boards = Boards.create(() -> boardSettings, orders);
        var npcBoards = new NpcBoards(plugin, boards, () -> boardBindings, () -> {}, (sender, failure) -> {});
        npcBoards.start(
                new NpcHooks(name -> name.equals("Citizens"), className -> citizens, Logger.getAnonymousLogger()));
        return new AdminServerWrites(new AdminServerWrites.Sources(
                boards,
                npcBoards,
                server.getConsoleSender(),
                () -> nodeData,
                node -> "Redis",
                admin -> {
                    if (failSync) throw new IllegalStateException("the database is away");
                    configSyncs.incrementAndGet();
                },
                views::instant,
                Runnable::run,
                adminLog,
                Logger.getAnonymousLogger(),
                failure -> "text of " + failure.localeKey()));
    }

    private static Plugin configPlugin(Path folder) {
        return (Plugin) Proxy.newProxyInstance(
                DashboardWorld.class.getClassLoader(), new Class<?>[] {Plugin.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getDataFolder" -> folder.toFile();
                        case "getLogger" -> Logger.getAnonymousLogger();
                        case "getName" -> "Companies";
                        default -> null;
                    };
                });
    }

    /**
     * Creates the world in a temporary directory.
     *
     * @param directory the directory of the database
     * @return the world
     */
    public static DashboardWorld create(Path directory) throws Exception {
        return new DashboardWorld(directory);
    }

    /**
     * Names a new player.
     *
     * @param name the name
     * @return the player's id
     */
    public UUID player(String name) {
        var uuid = UUID.randomUUID();
        names.put(uuid, name);
        return uuid;
    }

    private String name(UUID uuid) {
        return names.getOrDefault(uuid, uuid.toString());
    }

    /**
     * A player as an offline player that knows its id and name.
     *
     * @param uuid the player's id
     * @return the player
     */
    public OfflinePlayer offline(UUID uuid) {
        return (OfflinePlayer) Proxy.newProxyInstance(
                OfflinePlayer.class.getClassLoader(),
                new Class[] {OfflinePlayer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getName" -> name(uuid);
                    case "isOnline" -> false;
                    case "getPlayer" -> null;
                    case "hashCode" -> uuid.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> "OfflinePlayer(" + uuid + ")";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * Founds a company with members.
     *
     * @param name    the company's name
     * @param members the members with their permissions
     * @return the company id
     */
    public int company(String name, Map<UUID, List<CompanyPermission>> members) {
        var id = companies.submitCompanyCreation(name, JoinMode.INVITE_ONLY).join();
        members.forEach((member, permissions) -> companies
                .submitMemberUpdate(CompanyMember.of(
                        id,
                        member,
                        CompanyPermission.composePermissions(permissions.toArray(CompanyPermission[]::new))))
                .join());
        return id;
    }

    /**
     * Adds a member to a company.
     *
     * @param company     the company id
     * @param member      the member
     * @param permissions the member's permissions
     */
    public void member(int company, UUID member, CompanyPermission... permissions) {
        companies
                .submitMemberUpdate(
                        CompanyMember.of(company, member, CompanyPermission.composePermissions(permissions)))
                .join();
    }

    /**
     * Places an order of 10 stone for 100 and 5 dirt for 50.
     *
     * @param owner the owner
     * @param name  the order's name
     * @return the order
     */
    public SimpleOrder order(UUID owner, String name) {
        var player = SqLiteTestDatabase.player(owner);
        var contents = List.of(
                new OrderContent(0, new ItemStack(Material.STONE), false, 10, 100.0),
                new OrderContent(1, new ItemStack(Material.DIRT), false, 5, 50.0));
        orders.submitOrder(player, new SimpleOrder(owner, name, 0).toFullOrder(contents))
                .join();
        var placed = orders.retrieveOrdersByPlayer(player, OrderState.UNCLAIMED, OrderState.RECEIVED)
                .join();
        return placed.getLast();
    }

    /**
     * Lets a company claim an order.
     *
     * @param order   the order
     * @param company the company
     */
    public void claim(SimpleOrder order, int company) {
        query("UPDATE order_states SET state = ?, company = ?, last_update = ? WHERE id = ?")
                .single(call().bind(OrderState.CLAIMED.stateId())
                        .bind(company)
                        .bind(SqLiteAdapter.format(Times.now()))
                        .bind(order.id()))
                .update();
        orders.forgetFullOrder(order.id());
    }

    /**
     * Sets the state of an order directly.
     *
     * @param order   the order
     * @param state   the state
     * @param company the company working on it
     */
    public void state(SimpleOrder order, OrderState state, int company) {
        SqLiteTestDatabase.setState(order.id(), state.stateId(), company);
        orders.forgetFullOrder(order.id());
    }

    /**
     * Records a delivery of a worker.
     *
     * @param order  the order
     * @param worker the worker
     * @param amount how many stone
     */
    public void deliver(SimpleOrder order, UUID worker, int amount) {
        query("INSERT INTO order_deliveries(order_id, line, worker_uuid, stack, amount) VALUES (?, ?, ?, ?, ?)")
                .single(call().bind(order.id())
                        .bind(0)
                        .bind(worker, UUID_BYTES)
                        .bind("{\"type\":\"STONE\",\"amount\":1}")
                        .bind(amount))
                .insert();
        orders.forgetFullOrder(order.id());
    }

    /**
     * Creates a goal for oak logs paid with minted money: 100 items at 2 each in chunks of 10, running for a day.
     *
     * @param name   the goal's name
     * @param starts hours from now until it starts, negative for a start in the past
     * @param active whether it runs already, started as the primary starts it
     * @return the goal's id
     */
    public int goal(String name, int starts, boolean active) {
        var now = Times.now();
        var id = offers.create(EventOffer.goal(
                        name,
                        OfferLine.material("OAK_LOG"),
                        100,
                        2,
                        10,
                        3,
                        MoneySource.SourceType.MINTED,
                        null,
                        200,
                        now.plusHours(starts),
                        now.plusHours(starts + 24L),
                        "Ada",
                        now))
                .orElseThrow();
        if (active) offers.activate(id, 0, now);
        return id;
    }

    /**
     * Creates a contest for oak logs paid from the account {@code bank}: 100 items at 1 each with prizes of 50 and 20,
     * running for a day.
     *
     * @param name   the contest's name
     * @param starts hours from now until it starts
     * @param active whether it runs already, which also takes its money
     * @return the contest's id
     */
    public int contest(String name, int starts, boolean active) {
        var now = Times.now();
        var id = offers.create(EventOffer.contest(
                        name,
                        OfferLine.material("OAK_LOG"),
                        100,
                        1,
                        List.of(50.0, 20.0),
                        MoneySource.SourceType.ACCOUNT,
                        "bank",
                        170,
                        now.plusHours(starts),
                        now.plusHours(starts + 24L),
                        "Ada",
                        now))
                .orElseThrow();
        if (active) offers.activate(id, 170, now);
        return id;
    }

    /**
     * Records a delivered chunk of a goal.
     *
     * @param offer   the goal
     * @param order   the chunk's order
     * @param company the company that delivered it, 0 for a player alone
     * @param solo    the player who delivered it alone, null for a company
     * @param items   the items delivered
     */
    public void deliveredChunk(int offer, int order, int company, @Nullable UUID solo, int items) {
        query("""
                INSERT INTO event_offer_chunks(order_id, offer_id, amount, delivered, paid, state, company, solo_worker,
                                               placed, ended)
                VALUES (?, ?, ?, ?, ?, 'DELIVERED', ?, ?, ?, ?)""")
                .single(call().bind(order)
                        .bind(offer)
                        .bind(items)
                        .bind(items)
                        .bind(items * 2.0)
                        .bind(company == 0 ? null : company)
                        .bind(solo, UUID_BYTES)
                        .bind(SqLiteAdapter.format(Times.now()))
                        .bind(SqLiteAdapter.format(Times.now())))
                .insert();
    }

    /**
     * A wallet kept in {@link #wallets}.
     */
    private final class Wallet implements OrderCreationService.Wallet {
        @Override
        public synchronized boolean withdraw(OfflinePlayer player, double amount) {
            var current = wallets.getOrDefault(player.getUniqueId(), 0.0);
            if (current < amount) return false;
            wallets.put(player.getUniqueId(), current - amount);
            return true;
        }

        @Override
        public synchronized void deposit(OfflinePlayer player, double amount) {
            wallets.merge(player.getUniqueId(), amount, Double::sum);
        }

        @Override
        public String format(double amount) {
            return String.valueOf(amount);
        }

        @Override
        public String currencyName() {
            return "coins";
        }
    }

    /**
     * Repeating orders tell no one in these tests.
     */
    private static final class SilentNotices implements SeriesService.Notices {
        @Override
        public void posted(OrderSeries series, OrderCreationService.Created copy, int number) {}

        @Override
        public void waitingLimit(OrderSeries series) {}

        @Override
        public void stopped(OrderSeries series) {}

        @Override
        public void finished(OrderSeries series) {}
    }

    /**
     * Membership events go to {@link #events}; joins and profile edits are not recorded.
     */
    private final class MembershipEvents implements MembershipService.Events {
        @Override
        public void joined(CompanyProfile company, OfflinePlayer player) {}

        @Override
        public void profileEdited(CompanyProfile previous, CompanyProfile company, OfflinePlayer player) {}

        @Override
        public void fire(Event event) {
            events.accept(event);
        }
    }

    /**
     * The same wallet for the treasury, which reports whether a payment happened.
     */
    private final class TreasuryWallet implements TreasuryService.Wallet {
        private final Wallet wallet = new Wallet();

        @Override
        public boolean withdraw(OfflinePlayer player, double amount) {
            return wallet.withdraw(player, amount);
        }

        @Override
        public boolean deposit(OfflinePlayer player, double amount) {
            wallet.deposit(player, amount);
            return true;
        }

        @Override
        public String format(double amount) {
            return String.valueOf(amount);
        }
    }

    @Override
    public void close() {
        TestExecutors.drain(executor);
        MockBukkit.unmock();
    }

    /**
     * Deliveries that record what was asked: one line delivers the asked amount up to what it misses and never completes
     * the order, everything delivers what every line misses and completes it. The item matching itself needs Paper's
     * item stacks.
     */
    public static final class RecordedDeliveries implements ItemWrites.Deliveries {
        public final List<String> calls = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<DeliveryResult> line(
                Player worker, CompanyProfile company, FullOrder order, int line, int amount) {
            calls.add("line " + order.id() + " " + line + " " + amount);
            var missing = order.line(line).orElseThrow().missing();
            var delivered = Math.min(missing, amount);
            return CompletableFuture.completedFuture(new DeliveryResult(Map.of(line, delivered), order, false));
        }

        @Override
        public CompletableFuture<DeliveryResult> all(Player worker, CompanyProfile company, FullOrder order) {
            calls.add("all " + order.id());
            Map<Integer, Integer> lines = new HashMap<>();
            order.contents().forEach(content -> lines.put(content.line(), content.missing()));
            return CompletableFuture.completedFuture(new DeliveryResult(lines, order, true));
        }
    }
}
