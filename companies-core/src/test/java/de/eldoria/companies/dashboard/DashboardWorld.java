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
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.ItemMatchSettings;
import de.eldoria.companies.configuration.elements.NotificationSettings;
import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.dashboard.actions.DashboardWrites;
import de.eldoria.companies.dashboard.views.DashboardReads;
import de.eldoria.companies.dashboard.views.Views;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteAdapter;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteAlertData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteCompanyData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteDashboardData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteHistoryData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteInboxData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteMembershipData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteStatsData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTreasuryData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.EventRecorder;
import de.eldoria.companies.orders.CompanyOrderService;
import de.eldoria.companies.orders.HistoryRecorder;
import de.eldoria.companies.orders.ItemMatcher;
import de.eldoria.companies.orders.OrderAcceptService;
import de.eldoria.companies.orders.OrderCreationService;
import de.eldoria.companies.orders.OrderOutcomes;
import de.eldoria.companies.orders.OwnerOrderService;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.services.notifications.Inbox;
import de.eldoria.companies.util.TestExecutors;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * A server with a SQLite database, companies, orders and the dashboard's views on top, for dashboard tests.
 */
public final class DashboardWorld implements AutoCloseable {
    public final ServerMock server;
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
    public final EventRecorder events = new EventRecorder();
    public final Map<UUID, Double> wallets = new ConcurrentHashMap<>();
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
                        server::getOfflinePlayer, failure -> Component.text(failure.localeKey()), LocalDateTime::now),
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
                ZoneId.systemDefault(),
                Clock.systemUTC()));
        var configuration = new Configuration(MockBukkit.createMockPlugin()) {
            @Override
            public CompanySettings companySettings() {
                return companySettings;
            }
        };
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
                        entry -> entry.type().name(),
                        key -> "text of " + key,
                        this::name,
                        Clock.systemUTC()));
        var plugin = MockBukkit.createMockPlugin();
        var outcomes = new OrderOutcomes(
                orders,
                new HistoryRecorder(new SqLiteHistoryData(SqLiteTestDatabase.mapper(), executor), Clock.systemUTC()),
                new ItemReturnService(plugin, orders),
                events,
                ISimpleCompany::forId,
                Logger.getAnonymousLogger(),
                executor);
        writes = new DashboardWrites(new DashboardWrites.Sources(
                player -> companies
                        .retrievePlayerCompanyProfile(SqLiteTestDatabase.player(player))
                        .asFuture(),
                new OrderAcceptService(orders, () -> companySettings, events, uuid -> Optional.empty(), executor),
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
                Clock.systemUTC()));
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
                        .bind(SqLiteAdapter.format(LocalDateTime.now()))
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

    @Override
    public void close() {
        TestExecutors.drain(executor);
        MockBukkit.unmock();
    }
}
