/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard;

import de.eldoria.companies.api.CompaniesApiImpl;
import de.eldoria.companies.commands.company.order.search.SearchExecutor;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.components.order.OrderSource;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.ItemMatchSettings;
import de.eldoria.companies.configuration.elements.NotificationSettings;
import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.configuration.elements.UserSettings;
import de.eldoria.companies.configuration.elements.companysettings.ProfileSettings;
import de.eldoria.companies.dashboard.actions.CompanyWrites;
import de.eldoria.companies.dashboard.actions.DashboardWrites;
import de.eldoria.companies.dashboard.actions.ItemWrites;
import de.eldoria.companies.dashboard.actions.OrderCreateWrites;
import de.eldoria.companies.dashboard.market.MarketHistory;
import de.eldoria.companies.dashboard.views.DashboardReads;
import de.eldoria.companies.dashboard.views.OrderCatalogue;
import de.eldoria.companies.dashboard.views.Views;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteAdapter;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteAlertData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteCompanyData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteDashboardData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteHistoryData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteInboxData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteMembershipData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteSavedItemData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteSeriesData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteStatsData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTreasuryData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.data.wrapper.series.OrderSeries;
import de.eldoria.companies.events.EventRecorder;
import de.eldoria.companies.orders.CompanyOrderService;
import de.eldoria.companies.orders.DeliveryResult;
import de.eldoria.companies.orders.HistoryRecorder;
import de.eldoria.companies.orders.ItemMatcher;
import de.eldoria.companies.orders.OrderAcceptService;
import de.eldoria.companies.orders.OrderCreationService;
import de.eldoria.companies.orders.OrderDraftService;
import de.eldoria.companies.orders.OrderLimits;
import de.eldoria.companies.orders.OrderOutcomes;
import de.eldoria.companies.orders.OrderReceiveService;
import de.eldoria.companies.orders.OwnerOrderService;
import de.eldoria.companies.orders.SavedItemService;
import de.eldoria.companies.services.CompanyChanges;
import de.eldoria.companies.services.CompanyService;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.services.MemberService;
import de.eldoria.companies.services.MembershipService;
import de.eldoria.companies.services.ProfileService;
import de.eldoria.companies.services.SeriesService;
import de.eldoria.companies.services.TreasuryService;
import de.eldoria.companies.services.notifications.Inbox;
import de.eldoria.companies.util.TestExecutors;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
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
    public final ExecutorService executor = Executors.newFixedThreadPool(4);
    public final Map<UUID, String> names = new ConcurrentHashMap<>();
    public final SqLiteCompanyData companies;
    public final SqLiteOrderData orders;
    public final SqLiteTreasuryData treasury;
    public final SqLiteMembershipData memberships;
    public final SqLiteInboxData inbox;
    public final SqLiteDashboardData dashboard;
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
    public final List<Integer> changedOrders = new CopyOnWriteArrayList<>();
    public final List<UUID> changedInboxes = new CopyOnWriteArrayList<>();
    public final CompanySettings companySettings = new CompanySettings();
    public final OrderSettings orderSettings = new OrderSettings();

    private DashboardWorld(Path directory) throws Exception {
        server = MockBukkit.mock();
        SqLiteTestDatabase.create(directory);
        companies = new SqLiteCompanyData(executor);
        orders = new SqLiteOrderData(null, SqLiteTestDatabase.mapper());
        treasury = new SqLiteTreasuryData(executor);
        memberships = new SqLiteMembershipData(executor);
        inbox = new SqLiteInboxData(executor);
        dashboard = new SqLiteDashboardData();
        var stats = new SqLiteStatsData(executor);
        var alerts = new SqLiteAlertData(SqLiteTestDatabase.mapper(), executor);
        api = new CompaniesApiImpl(
                new CompaniesApiImpl.Repositories(companies, orders, stats, treasury),
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
                Clock.systemUTC()));
        var configuration = new Configuration(MockBukkit.createMockPlugin()) {
            @Override
            public CompanySettings companySettings() {
                return companySettings;
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
                SqLiteTestDatabase::player);
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
                treasuryService,
                () -> "coins",
                failure -> "text of " + failure.localeKey(),
                text -> "text of " + text.build()));
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
                        failure -> "text of " + failure.localeKey())));
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
