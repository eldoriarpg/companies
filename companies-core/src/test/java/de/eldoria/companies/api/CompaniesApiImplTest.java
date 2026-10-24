/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.api.write.CreateOrderResult;
import de.eldoria.companies.api.write.OrderLineRequest;
import de.eldoria.companies.api.write.OrderRequest;
import de.eldoria.companies.api.write.RejectReason;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.components.company.TreasuryKind;
import de.eldoria.companies.components.join.JoinResult;
import de.eldoria.companies.components.order.OrderSource;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.components.ranking.TopOrder;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.configuration.elements.UserSettings;
import de.eldoria.companies.configuration.elements.ordersettings.OrderFee;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteCompanyData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteMembershipData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteStatsData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTreasuryData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.treasury.TreasuryEntry;
import de.eldoria.companies.events.order.OrderCreatedEvent;
import de.eldoria.companies.events.order.PreOrderCreateEvent;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import de.eldoria.companies.orders.OrderCreationService;
import de.eldoria.companies.orders.OrderLimits;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.services.MembershipService;
import de.eldoria.companies.util.TestExecutors;
import de.eldoria.companies.util.Times;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompaniesApiImplTest {
    private final OrderSettings orderSettings = new OrderSettings() {
        @Override
        public OrderFee fee() {
            return new OrderFee(0.0, 10.0, 0.0, 0.0);
        }
    };
    private final UserSettings userSettings = new UserSettings();
    private final CompanySettings companySettings = new CompanySettings();
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();

    @TempDir
    Path directory;

    private ServerMock server;
    private Plugin plugin;
    private ExecutorService executor;
    private SqLiteCompanyData companyData;
    private SqLiteOrderData orderData;
    private SqLiteTreasuryData treasuryData;
    private CompaniesApiImpl api;
    private OfflinePlayer owner;
    private int miners;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(4);
        companyData = new SqLiteCompanyData(executor);
        orderData = new SqLiteOrderData(null, SqLiteTestDatabase.mapper());
        var stats = new SqLiteStatsData(executor);
        treasuryData = new SqLiteTreasuryData(executor);
        orderSettings.maxMaterials(2);
        userSettings.maxOrders(5);
        owner = server.getOfflinePlayer(UUID.randomUUID());
        balances.put(owner.getUniqueId(), 100.0);
        miners = companyData.submitCompanyCreation("Miners", JoinMode.OPEN).join();
        companyData
                .submitMemberUpdate(CompanyMember.of(miners, UUID.randomUUID(), CompanyPermission.OWNER.mask()))
                .join();

        api = new CompaniesApiImpl(
                new CompaniesApiImpl.Repositories(companyData, orderData, stats, treasuryData),
                new CompaniesApiImpl.Server(
                        server::getOfflinePlayer,
                        failure -> failure.message().orElse(Component.text(failure.localeKey())),
                        LocalDateTime::now),
                executor);
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
        MockBukkit.unmock();
    }

    @Test
    void registeredApiIsLoadedThroughTheEntryPoint() {
        api.register(plugin);

        assertEquals(api, CompaniesApi.get());

        api.unregister(plugin);

        assertThrows(IllegalStateException.class, CompaniesApi::get);
    }

    @Test
    void writesFailBeforeTheApiIsOpened() {
        assertNotReady(api.companies().addMember(miners, UUID.randomUUID()));
        assertNotReady(api.orders().create(stoneRequest(true)));
        assertNotReady(api.players().grantItems(owner.getUniqueId(), new ItemStack(Material.STONE), 3));
        assertEquals(100.0, balance());
    }

    @Test
    void writesFailAgainOnceUnregistered() {
        open();
        api.register(plugin);
        api.unregister(plugin);

        assertNotReady(api.orders().create(stoneRequest(true)));
    }

    @Test
    void readsWorkBeforeTheApiIsOpened() throws Exception {
        var company = api.companies().byName("miners").get(5, TimeUnit.SECONDS).orElseThrow();

        assertEquals(miners, company.id());
        assertEquals(1, company.members().size());
        assertTrue(api.companies().byId(999).get(5, TimeUnit.SECONDS).isEmpty());
        assertTrue(api.companies().stats(miners).get(5, TimeUnit.SECONDS).isPresent());
        assertTrue(api.companies().stats(999).get(5, TimeUnit.SECONDS).isEmpty());
        assertTrue(api.companies()
                .memberStats(miners, TimeRange.LAST_7_DAYS)
                .get(5, TimeUnit.SECONDS)
                .isEmpty());
        assertEquals(0, api.players().itemsWaiting(owner.getUniqueId()).get(5, TimeUnit.SECONDS));
    }

    @Test
    void treasuryIsReadForKnownCompaniesOnly() throws Exception {
        treasuryData
                .submitCredit(miners, 1234, new TreasuryEntry(TreasuryKind.DEPOSIT, null, null, null, Times.now()))
                .join();

        var treasury = api.companies().treasury(miners).get(5, TimeUnit.SECONDS).orElseThrow();

        assertEquals(miners, treasury.company());
        assertEquals(new BigDecimal("12.34"), treasury.balance());
        assertEquals(0, treasury.payoutShare());
        assertTrue(api.companies().treasury(999).get(5, TimeUnit.SECONDS).isEmpty());
    }

    @Test
    void chargedOrderTakesPriceAndFeeFromTheOwner() throws Exception {
        open();
        var created = new CopyOnWriteArrayList<OrderCreatedEvent>();
        listen(OrderCreatedEvent.class, created::add);

        var result = api.orders().create(stoneRequest(true)).get(5, TimeUnit.SECONDS);

        var order = assertInstanceOf(CreateOrderResult.Created.class, result).order();
        assertEquals(20.0, order.price());
        assertEquals(2.0, order.fee());
        assertEquals(78.0, balance());
        assertEquals(1, created.size());
        assertEquals(OrderSource.API, created.get(0).source());
        var stored = api.orders()
                .byOwner(owner.getUniqueId(), OrderState.UNCLAIMED, OrderState.RECEIVED)
                .get(5, TimeUnit.SECONDS);
        assertEquals(1, stored.size());
        assertEquals(order.id(), stored.get(0).id());
        assertEquals(
                order.id(),
                api.orders()
                        .byId(order.id())
                        .get(5, TimeUnit.SECONDS)
                        .orElseThrow()
                        .id());
    }

    @Test
    void serverFundedOrderLeavesTheOwnerUntouched() throws Exception {
        open();
        balances.put(owner.getUniqueId(), 0.0);

        var result = api.orders().create(stoneRequest(false)).get(5, TimeUnit.SECONDS);

        var order = assertInstanceOf(CreateOrderResult.Created.class, result).order();
        assertEquals(20.0, order.price());
        assertEquals(0.0, order.fee());
        assertEquals(0.0, balance());
    }

    @Test
    void ownerWhoCanNotPayIsRejected() throws Exception {
        open();
        balances.put(owner.getUniqueId(), 5.0);

        var result = api.orders().create(stoneRequest(true)).get(5, TimeUnit.SECONDS);

        var rejected = assertInstanceOf(CreateOrderResult.Rejected.class, result);
        assertEquals(RejectReason.INSUFFICIENT_FUNDS, rejected.reason());
        assertEquals(5.0, balance());
    }

    @Test
    void listenerCancellingThePreEventRejectsWithItsReason() throws Exception {
        open();
        var reason = Component.text("Quest not finished");
        listen(PreOrderCreateEvent.class, event -> event.cancel(reason));

        var result = api.orders().create(stoneRequest(true)).get(5, TimeUnit.SECONDS);

        var rejected = assertInstanceOf(CreateOrderResult.Rejected.class, result);
        assertEquals(RejectReason.CANCELLED, rejected.reason());
        assertEquals(reason, rejected.message());
        assertEquals(100.0, balance());
    }

    @Test
    void adjustedContentsAreValidatedAgain() throws Exception {
        open();
        listen(PreOrderCreateEvent.class, event -> {
            event.draft().addLine(Material.DIRT, 1, 1.0);
            event.draft().addLine(Material.SAND, 1, 1.0);
        });

        var result = api.orders().create(stoneRequest(true)).get(5, TimeUnit.SECONDS);

        var rejected = assertInstanceOf(CreateOrderResult.Rejected.class, result);
        assertEquals(RejectReason.INVALID, rejected.reason());
        assertEquals(Component.text("error.integrationDraftInvalid"), rejected.message());
        assertEquals(100.0, balance());
    }

    @Test
    void adjustedPriceIsChargedWithItsFee() throws Exception {
        open();
        listen(PreOrderCreateEvent.class, event -> event.draft().lines().get(0).unitPrice(4.0));

        var result = api.orders().create(stoneRequest(true)).get(5, TimeUnit.SECONDS);

        var order = assertInstanceOf(CreateOrderResult.Created.class, result).order();
        assertEquals(40.0, order.price());
        assertEquals(4.0, order.fee());
        assertEquals(56.0, balance());
    }

    @Test
    void addedMemberJoinsTheCompany() throws Exception {
        open();
        var player = UUID.randomUUID();

        assertEquals(
                JoinResult.JOINED, api.companies().addMember(miners, player).get(5, TimeUnit.SECONDS));
        assertEquals(
                miners,
                api.companies()
                        .of(player)
                        .get(5, TimeUnit.SECONDS)
                        .orElseThrow()
                        .id());
        assertEquals(
                JoinResult.UNKNOWN_COMPANY,
                api.companies().addMember(999, UUID.randomUUID()).get(5, TimeUnit.SECONDS));
    }

    @Test
    void grantedItemsWaitForTheOwnerAndFireTheEvent() throws Exception {
        open();
        var returned = new CopyOnWriteArrayList<ItemsReturnedEvent>();
        listen(ItemsReturnedEvent.class, returned::add);

        api.players()
                .grantItems(owner.getUniqueId(), new ItemStack(Material.DIAMOND, 7), 130)
                .get(5, TimeUnit.SECONDS);

        assertEquals(130, api.players().itemsWaiting(owner.getUniqueId()).get(5, TimeUnit.SECONDS));
        assertEquals(1, returned.size());
        assertEquals(ItemsReturnedEvent.Reason.API, returned.get(0).reason());
        assertEquals(130, returned.get(0).stacks().get(0).amount());
        assertTrue(returned.get(0).order().isEmpty());
    }

    @Test
    void invalidGrantsAreRefusedAtOnce() {
        assertThrows(IllegalArgumentException.class, () -> api.players()
                .grantItems(owner.getUniqueId(), new ItemStack(Material.STONE), 0));
        assertThrows(IllegalArgumentException.class, () -> api.players()
                .grantItems(owner.getUniqueId(), ItemStack.empty(), 1));
    }

    @Test
    void soloOrdersAreFoundByTheirWorker() throws Exception {
        open();
        var order = assertInstanceOf(
                        CreateOrderResult.Created.class,
                        api.orders().create(stoneRequest(false)).get(5, TimeUnit.SECONDS))
                .order();
        var worker = UUID.randomUUID();
        var stored = orderData.retrieveOrderById(order.id()).join().orElseThrow();
        assertTrue(orderData.submitSoloClaim(worker, stored, 1).join());

        var found = api.orders()
                .bySoloWorker(worker, OrderState.CLAIMED, OrderState.CLAIMED)
                .get(5, TimeUnit.SECONDS);

        assertEquals(1, found.size());
        assertEquals(order.id(), found.get(0).id());
        assertEquals(Optional.of(worker), found.get(0).soloWorker());
        assertTrue(api.orders()
                .bySoloWorker(UUID.randomUUID(), OrderState.UNCLAIMED, OrderState.RECEIVED)
                .get(5, TimeUnit.SECONDS)
                .isEmpty());
    }

    @Test
    void rankingRefusesPagesBelowOne() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> api.ranking().page(TopOrder.ORDERS, TimeRange.ALL, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> api.ranking().page(TopOrder.ORDERS, TimeRange.ALL, 1, 0));
        assertTrue(api.ranking()
                .rankOf(999, TopOrder.ORDERS, TimeRange.ALL)
                .get(5, TimeUnit.SECONDS)
                .isEmpty());
    }

    private void open() {
        var creation = new OrderCreationService(
                orderData,
                () -> orderSettings,
                new OrderLimits(orderData, () -> userSettings),
                item -> Optional.empty(),
                new Wallet(),
                server.getPluginManager()::callEvent,
                Runnable::run,
                Logger.getLogger("test"));
        var memberships = new MembershipService(
                companyData,
                new SqLiteMembershipData(executor),
                () -> companySettings,
                (recipient, type, args) -> {},
                MembershipService.Events.bukkit(plugin),
                server::getOfflinePlayer,
                LocalDateTime::now,
                executor,
                Logger.getLogger("test"));
        api.open(new CompaniesApiImpl.Writes(creation, memberships, new ItemReturnService(plugin, orderData)));
    }

    private OrderRequest stoneRequest(boolean charge) {
        return OrderRequest.builder(owner.getUniqueId(), "Quest reward")
                .line(OrderLineRequest.material(Material.STONE, 10, 2.0))
                .charge(charge)
                .build();
    }

    private double balance() {
        return balances.get(owner.getUniqueId());
    }

    private static void assertNotReady(CompletableFuture<?> future) {
        var err = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
        assertInstanceOf(ApiNotReadyException.class, err.getCause());
    }

    private <T extends Event> void listen(Class<T> type, Consumer<T> handler) {
        server.getPluginManager()
                .registerEvent(
                        type,
                        new Listener() {},
                        EventPriority.NORMAL,
                        (listener, event) -> {
                            if (type.isInstance(event)) handler.accept(type.cast(event));
                        },
                        plugin);
    }

    private final class Wallet implements OrderCreationService.Wallet {
        @Override
        public boolean withdraw(OfflinePlayer player, double amount) {
            var balance = balances.get(player.getUniqueId());
            if (balance < amount) return false;
            balances.put(player.getUniqueId(), balance - amount);
            return true;
        }

        @Override
        public void deposit(OfflinePlayer player, double amount) {
            balances.merge(player.getUniqueId(), amount, Double::sum);
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
}
