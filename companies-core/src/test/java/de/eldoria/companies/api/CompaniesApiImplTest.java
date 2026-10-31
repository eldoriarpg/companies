/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.api.write.CreateEventResult;
import de.eldoria.companies.api.write.CreateOrderResult;
import de.eldoria.companies.api.write.EventChange;
import de.eldoria.companies.api.write.EventProblem;
import de.eldoria.companies.api.write.EventRequest;
import de.eldoria.companies.api.write.FoundProblem;
import de.eldoria.companies.api.write.FoundRequest;
import de.eldoria.companies.api.write.FoundResult;
import de.eldoria.companies.api.write.HomeProblem;
import de.eldoria.companies.api.write.HomeRequest;
import de.eldoria.companies.api.write.HomeResult;
import de.eldoria.companies.api.write.OrderLineRequest;
import de.eldoria.companies.api.write.OrderRequest;
import de.eldoria.companies.api.write.RejectReason;
import de.eldoria.companies.areas.AreaServices;
import de.eldoria.companies.areas.CompanyHomes;
import de.eldoria.companies.components.area.AreaBounds;
import de.eldoria.companies.components.area.AreaCapability;
import de.eldoria.companies.components.area.AreaProvider;
import de.eldoria.companies.components.area.AreaSource;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.components.company.TreasuryKind;
import de.eldoria.companies.components.event.EventKind;
import de.eldoria.companies.components.event.EventState;
import de.eldoria.companies.components.join.JoinResult;
import de.eldoria.companies.components.order.ItemCriteria;
import de.eldoria.companies.components.order.LineKind;
import de.eldoria.companies.components.order.OrderSource;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.components.ranking.TopOrder;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.configuration.elements.UserSettings;
import de.eldoria.companies.configuration.elements.areas.AreaSettings;
import de.eldoria.companies.configuration.elements.jobs.JobSettings;
import de.eldoria.companies.configuration.elements.ordersettings.OrderFee;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteAreaData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteCompanyData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteHomeData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteJobData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteMembershipData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteServerEventData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteStatsData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTreasuryData;
import de.eldoria.companies.data.wrapper.area.CompanyArea;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.data.wrapper.treasury.TreasuryEntry;
import de.eldoria.companies.events.company.PreCompanyCreateEvent;
import de.eldoria.companies.events.order.OrderCreatedEvent;
import de.eldoria.companies.events.order.PreOrderCreateEvent;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import de.eldoria.companies.orders.OrderCreationService;
import de.eldoria.companies.orders.OrderLimits;
import de.eldoria.companies.services.CompanyChanges;
import de.eldoria.companies.services.CompanyService;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.services.MembershipService;
import de.eldoria.companies.services.events.ServerEventService;
import de.eldoria.companies.util.TestExecutors;
import de.eldoria.companies.util.Times;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private SqLiteServerEventData serverEventData;
    private SqLiteAreaData areaData;
    private SqLiteHomeData homeData;
    private volatile boolean cancelFoundings;
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
        serverEventData = new SqLiteServerEventData();
        areaData = new SqLiteAreaData();
        homeData = new SqLiteHomeData();
        orderSettings.maxMaterials(2);
        userSettings.maxOrders(5);
        owner = server.getOfflinePlayer(UUID.randomUUID());
        balances.put(owner.getUniqueId(), 100.0);
        miners = companyData.submitCompanyCreation("Miners", JoinMode.OPEN).join();
        companyData
                .submitMemberUpdate(CompanyMember.of(miners, UUID.randomUUID(), CompanyPermission.OWNER.mask()))
                .join();

        api = new CompaniesApiImpl(
                new CompaniesApiImpl.Repositories(
                        companyData,
                        orderData,
                        stats,
                        treasuryData,
                        serverEventData,
                        areaData,
                        homeData,
                        new SqLiteJobData()),
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
        assertNotReady(api.companies().found(new FoundRequest(UUID.randomUUID(), "Late", false)));
        assertNotReady(api.companies()
                .setHome(new HomeRequest(
                        miners, new Location(server.addSimpleWorld("late"), 0, 64, 0), owner.getUniqueId())));
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
        api.open(new CompaniesApiImpl.Writes(
                creation,
                memberships,
                new ItemReturnService(plugin, orderData),
                new ServerEventService(
                        serverEventData,
                        material -> material.toUpperCase(Locale.ROOT),
                        message -> {},
                        Times.UTC,
                        Logger.getLogger("test")),
                new CompanyService(
                        companyData,
                        new CompanyChanges(
                                companyData,
                                server::getOfflinePlayer,
                                event -> {
                                    if (event instanceof PreCompanyCreateEvent pre) pre.setCancelled(cancelFoundings);
                                },
                                Logger.getLogger("test"),
                                executor),
                        null,
                        new Wallet(),
                        () -> companySettings,
                        server::getOfflinePlayer,
                        uuid -> Optional.ofNullable(server.getPlayer(uuid)),
                        Runnable::run,
                        executor,
                        Logger.getLogger("test")),
                new CompanyHomes(
                        new AreaServices(
                                api.areaProviders(), areaData, homeData, null, null, UUID.randomUUID(), () -> "hub"),
                        AreaSettings::new,
                        plugin),
                null,
                JobSettings::new,
                Runnable::run));
    }

    @Test
    void foundsCompaniesWithAndWithoutCharge() throws Exception {
        open();
        companySettings.foundingPrice(40);
        var free = server.getOfflinePlayer(UUID.randomUUID());

        var paid = api.companies()
                .found(new FoundRequest(owner.getUniqueId(), "Builders", true))
                .get(5, TimeUnit.SECONDS);
        var unpaid = api.companies()
                .found(new FoundRequest(free.getUniqueId(), "Farmers", false))
                .get(5, TimeUnit.SECONDS);

        var builders = assertInstanceOf(FoundResult.Founded.class, paid).company();
        assertEquals("Builders", builders.name());
        assertTrue(builders.member(owner).isPresent());
        assertEquals(60.0, balance());
        assertTrue(unpaid.founded());
        assertFalse(balances.containsKey(free.getUniqueId()));
    }

    @Test
    void foundingNamesWhyItWasRefused() throws Exception {
        open();
        var other = server.getOfflinePlayer(UUID.randomUUID());
        balances.put(other.getUniqueId(), 0.0);
        companySettings.foundingPrice(40);

        assertEquals(FoundProblem.NAME_TAKEN, problem(new FoundRequest(owner.getUniqueId(), "Miners", false)));
        assertEquals(FoundProblem.INVALID_NAME, problem(new FoundRequest(owner.getUniqueId(), " ", false)));
        assertEquals(FoundProblem.NO_MONEY, problem(new FoundRequest(other.getUniqueId(), "Poor", true)));
        api.companies()
                .found(new FoundRequest(owner.getUniqueId(), "First", false))
                .get(5, TimeUnit.SECONDS);
        assertEquals(FoundProblem.ALREADY_MEMBER, problem(new FoundRequest(owner.getUniqueId(), "Second", false)));
    }

    @Test
    void aCancelledFoundingIsReportedAsCancelled() throws Exception {
        open();
        cancelFoundings = true;
        var founder = server.addPlayer();

        assertEquals(FoundProblem.CANCELLED, problem(new FoundRequest(founder.getUniqueId(), "Rebels", false)));
    }

    private FoundProblem problem(FoundRequest request) throws Exception {
        var result = api.companies().found(request).get(5, TimeUnit.SECONDS);
        return assertInstanceOf(FoundResult.Rejected.class, result).problem();
    }

    @Test
    void setsTheHomeOfACompany() throws Exception {
        open();
        var world = server.addSimpleWorld("world");
        var setter = UUID.randomUUID();

        var result = api.companies()
                .setHome(new HomeRequest(miners, new Location(world, 10.5, 64, -3.5), setter))
                .get(5, TimeUnit.SECONDS);
        var missing = api.companies()
                .setHome(new HomeRequest(999, new Location(world, 0, 64, 0), setter))
                .get(5, TimeUnit.SECONDS);

        var home = assertInstanceOf(HomeResult.Stored.class, result).home();
        assertEquals("world", home.world());
        assertEquals(10.5, home.x());
        assertEquals(setter, homeData.home(miners).orElseThrow().setBy());
        assertEquals(
                HomeProblem.NO_COMPANY,
                assertInstanceOf(HomeResult.Rejected.class, missing).problem());
    }

    @Test
    void aHomeOutsideTheCompanysAreasIsRefused() throws Exception {
        open();
        var world = server.addSimpleWorld("world");
        api.areaProviders().register(plugin, new Elsewhere());
        areaData.add(new CompanyArea(
                0,
                miners,
                "elsewhere",
                "plot",
                "world",
                UUID.randomUUID(),
                AreaSource.LINKED,
                null,
                0,
                null,
                LocalDateTime.now(),
                0,
                false,
                false,
                false));

        var result = api.companies()
                .setHome(new HomeRequest(miners, new Location(world, 0, 64, 0), UUID.randomUUID()))
                .get(5, TimeUnit.SECONDS);

        assertEquals(
                HomeProblem.NOT_IN_AREA,
                assertInstanceOf(HomeResult.Rejected.class, result).problem());
        assertTrue(homeData.home(miners).isEmpty());
    }

    /**
     * A plot plugin whose only plot is never where the test stands.
     */
    private record Elsewhere() implements AreaProvider {
        @Override
        public String id() {
            return "elsewhere";
        }

        @Override
        public Set<AreaCapability> capabilities() {
            return Set.of();
        }

        @Override
        public List<String> areasAt(Location location) {
            return List.of();
        }

        @Override
        public boolean exists(String ref) {
            return ref.equals("plot");
        }

        @Override
        public Set<UUID> owners(String ref) {
            return Set.of();
        }

        @Override
        public Optional<AreaBounds> bounds(String ref) {
            return Optional.empty();
        }

        @Override
        public String name(String ref) {
            return ref;
        }
    }

    @Test
    void criteriaLinesAreCreatedThroughTheApi() throws Exception {
        open();
        var logs = ItemCriteria.of("#minecraft:logs");
        var request = OrderRequest.builder(owner.getUniqueId(), "Logs")
                .line(OrderLineRequest.criteria(logs, 10, 1.0))
                .charge(false)
                .build();

        var result = api.orders().create(request).get(5, TimeUnit.SECONDS);

        var order = assertInstanceOf(CreateOrderResult.Created.class, result).order();
        var line = orderData
                .retrieveFullOrder(
                        orderData.retrieveOrderById(order.id()).join().orElseThrow())
                .join()
                .contents()
                .getFirst();
        assertEquals(LineKind.CRITERIA, line.kind());
        assertEquals(logs, line.criteria().orElseThrow());
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

    @Test
    void serverEventWritesFailBeforeTheApiIsOpened() {
        var now = LocalDateTime.now(Times.UTC);
        assertNotReady(api.events()
                .create(
                        plugin,
                        EventRequest.of("Tax", EventKind.TAX_REDUCTION, 5, now.plusHours(1), now.plusHours(2))));
        assertNotReady(api.events().end(plugin, 1));
        assertNotReady(api.events().cancel(plugin, 1));
    }

    @Test
    void aServerEventIsCreatedReadAndNotedAsThePlugin() throws Exception {
        open();
        var now = LocalDateTime.now(Times.UTC).withNano(0);
        var request = EventRequest.of("Harvest", EventKind.REWARD_MULTIPLIER, 1.5, now.plusHours(1), now.plusHours(3))
                .withMaterials(List.of("wheat"))
                .mintedWithin(500);

        var result = api.events().create(plugin, request).get(5, TimeUnit.SECONDS);

        var event = assertInstanceOf(CreateEventResult.Created.class, result).event();
        assertEquals("Harvest", event.name());
        assertEquals(List.of("WHEAT"), event.materials());
        assertTrue(event.minted());
        assertEquals(500.0, event.budgetCap().orElseThrow());
        assertEquals(EventState.SCHEDULED, event.state());
        assertEquals(plugin.getName() + "@api", event.createdBy());
        assertEquals(
                event.id(),
                api.events()
                        .event(event.id())
                        .get(5, TimeUnit.SECONDS)
                        .orElseThrow()
                        .id());
        assertEquals(1, api.events().live().get(5, TimeUnit.SECONDS).size());
        assertEquals(1, api.events().recent(10).get(5, TimeUnit.SECONDS).size());
    }

    @Test
    void theMoneyOfAKindThatPaysNothingIsDropped() throws Exception {
        open();
        var now = LocalDateTime.now(Times.UTC);
        var request = EventRequest.of("Tax", EventKind.TAX_REDUCTION, 5, now.plusHours(1), now.plusHours(2))
                .paidFrom("bank", 100.0);

        var event = assertInstanceOf(
                        CreateEventResult.Created.class,
                        api.events().create(plugin, request).get(5, TimeUnit.SECONDS))
                .event();

        assertTrue(event.fundingAccount().isEmpty());
        assertTrue(event.budgetCap().isEmpty());
    }

    @Test
    void anInvalidServerEventIsRejectedWithItsProblem() throws Exception {
        open();
        var now = LocalDateTime.now(Times.UTC);
        var tooHigh = EventRequest.of("Tax", EventKind.TAX_REDUCTION, 150, now.plusHours(1), now.plusHours(2));
        var noBudget = EventRequest.of("Boost", EventKind.REWARD_MULTIPLIER, 2, now.plusHours(1), now.plusHours(2));

        var value = api.events().create(plugin, tooHigh).get(5, TimeUnit.SECONDS);
        var funding = api.events().create(plugin, noBudget).get(5, TimeUnit.SECONDS);

        assertEquals(
                EventProblem.VALUE,
                assertInstanceOf(CreateEventResult.Rejected.class, value).problem());
        assertEquals(
                EventProblem.ACCOUNT,
                assertInstanceOf(CreateEventResult.Rejected.class, funding).problem());
        assertTrue(api.events().live().get(5, TimeUnit.SECONDS).isEmpty());
    }

    @Test
    void aServerEventIsEndedOrCancelledAsThePlugin() throws Exception {
        open();
        var now = LocalDateTime.now(Times.UTC);
        var waiting = created(EventRequest.of("Fee", EventKind.FEE_REDUCTION, 50, now.plusHours(1), now.plusHours(2)));

        assertEquals(EventChange.REFUSED, api.events().end(plugin, waiting).get(5, TimeUnit.SECONDS));
        assertEquals(EventChange.DONE, api.events().cancel(plugin, waiting).get(5, TimeUnit.SECONDS));
        assertEquals(EventChange.CLOSED, api.events().cancel(plugin, waiting).get(5, TimeUnit.SECONDS));
        assertEquals(EventChange.UNKNOWN, api.events().end(plugin, 9999).get(5, TimeUnit.SECONDS));
        var cancelled = api.events().event(waiting).get(5, TimeUnit.SECONDS).orElseThrow();
        assertEquals(EventState.CANCELLED, cancelled.state());
        assertEquals(plugin.getName() + "@api", cancelled.closer().orElseThrow());
    }

    @Test
    void everyProblemAndChangeOfTheCoreHasAnApiValue() {
        for (var problem : ServerEvent.Problem.values())
            assertEquals(problem.name(), EventProblem.valueOf(problem.name()).name());
        for (var change : ServerEventService.Change.values())
            assertEquals(change.name(), EventChange.valueOf(change.name()).name());
    }

    private int created(EventRequest request) throws Exception {
        var result = api.events().create(plugin, request).get(5, TimeUnit.SECONDS);
        return assertInstanceOf(CreateEventResult.Created.class, result).event().id();
    }
}
