/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.components.order.HistoryOutcome;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.EconomySettings;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteHistoryData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteStatsData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.history.HistoryEntry;
import de.eldoria.companies.data.wrapper.order.DeliveredStack;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.data.wrapper.stats.PayoutRecord;
import de.eldoria.companies.events.EventRecorder;
import de.eldoria.companies.events.order.OrderRemovedEvent;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import de.eldoria.companies.orders.Collector;
import de.eldoria.companies.orders.HistoryRecorder;
import de.eldoria.companies.orders.OrderCompletionService;
import de.eldoria.companies.orders.OrderTable;
import de.eldoria.companies.services.AdminOrderService.Refusal;
import de.eldoria.companies.services.AdminOrderService.Result;
import de.eldoria.companies.util.TestExecutors;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminOrderServiceTest {
    private static final int COMPANY = 4;
    private static final double FEE = 5.0;
    private static final CompanyProfile PROFILE =
            new CompanyProfile(COMPANY, "Acme", LocalDateTime.now(), 1, CompanyDetails.NONE, List.of());

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final Map<UUID, Double> paid = new ConcurrentHashMap<>();
    private final Map<UUID, Double> refunded = new ConcurrentHashMap<>();
    private final AtomicReference<Double> collected = new AtomicReference<>(0.0);
    private final List<String> notices = new CopyOnWriteArrayList<>();
    private final Map<String, Double> deposits = new ConcurrentHashMap<>();

    @TempDir
    Path directory;

    private ServerMock server;
    private Plugin plugin;
    private ExecutorService executor;
    private OrderTable orders;
    private SqLiteHistoryData history;
    private HistoryRecorder recorder;
    private OrderCompletionService completion;
    private AdminOrderService service;
    private EventRecorder events;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        events = EventRecorder.listen(plugin, ItemsReturnedEvent.class);
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(8);
        orders = OrderTable.create();
        history = new SqLiteHistoryData(SqLiteTestDatabase.mapper(), executor);
        recorder = new HistoryRecorder(history, Clock.systemDefaultZone());
        var treasury = new OrderCompletionService.Treasury() {
            @Override
            public void pay(OfflinePlayer worker, double amount) {
                paid.merge(worker.getUniqueId(), amount, Double::sum);
            }

            @Override
            public void collect(double amount) {
                collected.accumulateAndGet(amount, Double::sum);
            }
        };
        completion = new OrderCompletionService(
                plugin,
                orders.orderData(),
                new SqLiteStatsData(executor),
                recorder,
                treasury,
                level -> 10.0,
                () -> 5.0,
                Clock.systemDefaultZone(),
                executor);
        var funds = new AdminOrderService.Funds() {
            @Override
            public void refund(UUID owner, double amount) {
                refunded.merge(owner, amount, Double::sum);
            }

            @Override
            public void collect(double amount) {
                collected.accumulateAndGet(amount, Double::sum);
            }
        };
        service = service(funds);
    }

    private AdminOrderService service(AdminOrderService.Funds funds) {
        return new AdminOrderService(
                orders.orderData(),
                id -> id == COMPANY ? Optional.of(PROFILE) : Optional.empty(),
                completion,
                recorder,
                funds,
                new RecordingNotices(notices),
                new ItemReturnService(plugin, orders.orderData()),
                events,
                order -> {},
                Logger.getAnonymousLogger(),
                executor);
    }

    private AdminOrderService serviceWithCollector(String account) {
        var settings = new EconomySettings();
        settings.collectorAccount(account);
        var configuration = new Configuration(plugin) {
            @Override
            public EconomySettings economySettings() {
                return settings;
            }
        };
        var collector = new Collector(plugin, recordingEconomy(), configuration);
        return service(new AdminOrderService.Funds() {
            @Override
            public void refund(UUID owner, double amount) {
                refunded.merge(owner, amount, Double::sum);
            }

            @Override
            public void collect(double amount) {
                collector.collect(amount);
            }
        });
    }

    private Economy recordingEconomy() {
        return (Economy) Proxy.newProxyInstance(
                Economy.class.getClassLoader(), new Class<?>[] {Economy.class}, (proxy, method, args) -> {
                    if (method.getName().equals("depositPlayer") && args[0] instanceof String account) {
                        deposits.merge(account, (double) args[1], Double::sum);
                        return new EconomyResponse((double) args[1], 0, EconomyResponse.ResponseType.SUCCESS, null);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
        MockBukkit.unmock();
    }

    private UUID owner() {
        return orders.owner().getUniqueId();
    }

    private SimpleOrder claimedWithDeliveries() {
        var order = orders.place(FEE);
        orders.state(order, OrderState.CLAIMED, COMPANY);
        orders.deliver(order, 0, Material.STONE, alice, 4);
        orders.deliver(order, 1, Material.DIRT, bob, 5);
        return orders.reload(order);
    }

    private static <T> T done(Result<T> result) {
        if (result instanceof Result.Done<T> performed) return performed.value();
        throw new AssertionError("Refused: " + result);
    }

    private static void refused(Refusal expected, Result<?> result) {
        assertEquals(expected, assertInstanceOf(Result.Refused.class, result).refusal());
    }

    private List<HistoryOutcome> companyOutcomes() {
        return history.retrieveCompanyHistory(COMPANY, 1, 10).join().stream()
                .map(HistoryEntry::outcome)
                .toList();
    }

    private List<HistoryOutcome> ownerOutcomes() {
        return history.retrieveOwnerHistory(owner(), 1, 10).join().stream()
                .map(HistoryEntry::outcome)
                .toList();
    }

    private int returned(UUID worker) {
        return orders.orderData()
                .retrieveItemReturnCount(worker, ReturnKind.WORKER)
                .join();
    }

    @Test
    void cancelWithRefundReturnsPriceAndFee() {
        var order = orders.full(orders.place(FEE));

        var money = AdminOrderService.cancelMoney(order, true);

        assertEquals(155.0, money.refund());
        assertEquals(0.0, money.collected());
    }

    @Test
    void cancelWithoutRefundCollectsPriceAndFee() {
        var order = orders.full(orders.place(FEE));

        var money = AdminOrderService.cancelMoney(order, false);

        assertEquals(0.0, money.refund());
        assertEquals(155.0, money.collected());
    }

    @Test
    void partlyDeliveredOrderRefundsTheMissingShare() {
        var order = orders.full(claimedWithDeliveries());

        assertEquals(60.0, AdminOrderService.undeliveredRefund(order));
    }

    @Test
    void refundOfThirdsIsRoundedToCents() {
        var content = new OrderContent(0, new ItemStack(Material.STONE), false, 3, 10.0);
        content.deliveries(List.of(new DeliveredStack(1, alice, new ItemStack(Material.STONE), 1)));
        var order = new SimpleOrder(owner(), "thirds", 0).toFullOrder(List.of(content));

        assertEquals(6.67, AdminOrderService.undeliveredRefund(order));
    }

    @Test
    void unclaimReturnsDeliveriesAndOpensTheOrder() {
        var order = claimedWithDeliveries();

        AdminOrderService.Unclaimed unclaimed = done(service.unclaim(order.id()).join());

        assertEquals(Set.of(alice, bob), unclaimed.workers());
        assertEquals(OrderState.UNCLAIMED, orders.reload(order).state());
        assertEquals(4, returned(alice));
        assertEquals(5, returned(bob));
        assertEquals(List.of(HistoryOutcome.UNCLAIMED_BY_ADMIN), companyOutcomes());
        assertTrue(ownerOutcomes().isEmpty());
        assertEquals(List.of("company unclaimed " + COMPANY, "owner reopened"), notices);
        assertTrue(refunded.isEmpty());
        assertTrue(paid.isEmpty());
    }

    @Test
    void unclaimReportsTheReturnedItemsOfEveryWorker() {
        var order = claimedWithDeliveries();

        done(service.unclaim(order.id()).join());

        var returned = events.of(ItemsReturnedEvent.class);
        assertEquals(2, returned.size());
        var byPlayer = returned.stream()
                .collect(Collectors.toMap(event -> event.player().getUniqueId(), event -> event));
        assertReturned(byPlayer.get(alice), order, Material.STONE, 4);
        assertReturned(byPlayer.get(bob), order, Material.DIRT, 5);
        assertTrue(events.of(OrderRemovedEvent.class).isEmpty());
    }

    @Test
    void cancelOfClaimedOrderFiresTheRemovalAndTheReturns() {
        var order = claimedWithDeliveries();

        done(service.cancel(order.id(), false).join());

        var removed = events.single(OrderRemovedEvent.class);
        assertEquals(OrderRemovedEvent.Reason.ADMIN, removed.reason());
        assertEquals(order.id(), removed.order().id());
        assertTrue(removed.isAsynchronous());
        assertEquals(2, events.of(ItemsReturnedEvent.class).size());
    }

    @Test
    void cancelOfOpenOrderReturnsNoItems() {
        var order = orders.place(FEE);

        done(service.cancel(order.id(), true).join());

        assertEquals(
                OrderRemovedEvent.Reason.ADMIN,
                events.single(OrderRemovedEvent.class).reason());
        assertTrue(events.of(ItemsReturnedEvent.class).isEmpty());
    }

    @Test
    void refusedActionsFireNothing() {
        var order = claimedWithDeliveries();
        orders.state(order, OrderState.DELIVERED, COMPANY);

        refused(Refusal.NOT_OPEN, service.cancel(order.id(), true).join());
        refused(Refusal.NOT_CLAIMED, service.unclaim(order.id()).join());

        assertTrue(events.of(OrderRemovedEvent.class).isEmpty());
        assertTrue(events.of(ItemsReturnedEvent.class).isEmpty());
    }

    private static void assertReturned(ItemsReturnedEvent event, SimpleOrder order, Material material, int amount) {
        assertEquals(ItemsReturnedEvent.Reason.ADMIN_RELEASE, event.reason());
        assertEquals(order.id(), event.order().orElseThrow().id());
        assertEquals(1, event.stacks().size());
        assertEquals(material, event.stacks().get(0).stack().getType());
        assertEquals(amount, event.stacks().get(0).amount());
        assertTrue(event.isAsynchronous());
    }

    @Test
    void unclaimNeedsAClaimedOrder() {
        var order = orders.place(FEE);

        refused(Refusal.NOT_CLAIMED, service.unclaim(order.id()).join());
        refused(Refusal.UNKNOWN_ORDER, service.unclaim(order.id() + 100).join());

        assertTrue(notices.isEmpty());
        assertTrue(companyOutcomes().isEmpty());
    }

    @Test
    void cancelOfClaimedOrderRefundsAndRecordsBothSides() {
        var order = claimedWithDeliveries();

        AdminOrderService.Cancelled cancelled =
                done(service.cancel(order.id(), true).join());

        assertTrue(cancelled.wasClaimed());
        assertTrue(orders.orderData().retrieveOrderById(order.id()).join().isEmpty());
        assertEquals(Map.of(owner(), 155.0), refunded);
        assertEquals(0.0, collected.get());
        assertEquals(4, returned(alice));
        assertEquals(List.of(HistoryOutcome.CANCELLED_BY_ADMIN), ownerOutcomes());
        assertEquals(List.of(HistoryOutcome.CANCELLED_BY_ADMIN), companyOutcomes());
        assertEquals(List.of("company cancelled " + COMPANY, "owner cancelled 155.0"), notices);
    }

    @Test
    void cancelOfOpenOrderWithoutRefundCollectsPriceAndFee() {
        var order = orders.place(FEE);

        AdminOrderService.Cancelled cancelled =
                done(service.cancel(order.id(), false).join());

        assertFalse(cancelled.wasClaimed());
        assertTrue(refunded.isEmpty());
        assertEquals(155.0, collected.get());
        assertEquals(new AdminOrderService.CancelMoney(0, 155.0), cancelled.money());
        assertEquals(List.of(HistoryOutcome.CANCELLED_BY_ADMIN), ownerOutcomes());
        assertTrue(companyOutcomes().isEmpty());
        assertEquals(List.of("owner cancelled 0.0"), notices);
    }

    @Test
    void cancelOfClaimedOrderWithoutRefundCollectsPriceAndFeeAndReturnsTheItems() {
        var order = claimedWithDeliveries();

        done(service.cancel(order.id(), false).join());

        assertTrue(refunded.isEmpty());
        assertEquals(155.0, collected.get());
        assertEquals(4, returned(alice));
        assertEquals(5, returned(bob));
        assertEquals(List.of("company cancelled " + COMPANY, "owner cancelled 0.0"), notices);
    }

    @Test
    void cancelWithoutRefundDepositsPriceAndFeeToTheCollectorAccount() {
        var order = orders.place(FEE);

        done(serviceWithCollector("bank").cancel(order.id(), false).join());
        server.getScheduler().performOneTick();

        assertEquals(Map.of("bank", 155.0), deposits);
        assertTrue(refunded.isEmpty());
    }

    @Test
    void cancelWithoutRefundDepositsNothingWithoutACollectorAccount() {
        var order = orders.place(FEE);

        done(serviceWithCollector("").cancel(order.id(), false).join());
        server.getScheduler().performOneTick();

        assertTrue(deposits.isEmpty());
        assertTrue(refunded.isEmpty());
        assertTrue(orders.orderData().retrieveOrderById(order.id()).join().isEmpty());
    }

    @Test
    void cancelWithRefundDepositsNothingToTheCollectorAccount() {
        var order = orders.place(FEE);

        done(serviceWithCollector("bank").cancel(order.id(), true).join());
        server.getScheduler().performOneTick();

        assertTrue(deposits.isEmpty());
        assertEquals(Map.of(owner(), 155.0), refunded);
    }

    @Test
    void refusedCancelWithoutRefundMovesNoMoney() {
        var order = claimedWithDeliveries();
        orders.state(order, OrderState.DELIVERED, COMPANY);

        refused(
                Refusal.NOT_OPEN,
                serviceWithCollector("bank").cancel(order.id(), false).join());
        server.getScheduler().performOneTick();

        assertTrue(deposits.isEmpty());
        assertEquals(0.0, collected.get());
    }

    @Test
    void cancelRefusesDeliveredOrders() {
        var order = claimedWithDeliveries();
        orders.state(order, OrderState.DELIVERED, COMPANY);

        refused(Refusal.NOT_OPEN, service.cancel(order.id(), true).join());

        assertTrue(orders.orderData().retrieveOrderById(order.id()).join().isPresent());
        assertTrue(refunded.isEmpty());
    }

    @Test
    void deliverPaysWorkersForWhatTheyDeliveredAndRefundsTheRest() {
        var order = claimedWithDeliveries();

        AdminOrderService.Delivered delivered = done(service.deliver(order.id()).join());

        assertEquals(60.0, delivered.refund());
        assertEquals(Map.of(owner(), 60.0), refunded);
        assertEquals(Map.of(alice, 36.0, bob, 45.0), paid);
        assertEquals(FEE + 9.0, collected.get(), 0.001);
        var gross = delivered.completion().payouts().stream()
                .collect(Collectors.toMap(PayoutRecord::worker, PayoutRecord::gross));
        assertEquals(Map.of(alice, 40.0, bob, 50.0), gross);
        assertEquals(OrderState.DELIVERED, orders.reload(order).state());
        assertEquals(List.of(HistoryOutcome.DELIVERED), companyOutcomes());
        assertEquals(List.of("owner refunded 60.0"), notices);
    }

    @Test
    void deliverOfACompleteOrderRefundsNothing() {
        var order = orders.place(FEE);
        orders.state(order, OrderState.CLAIMED, COMPANY);
        orders.deliver(order, 0, Material.STONE, alice, 10);
        orders.deliver(order, 1, Material.DIRT, bob, 5);

        AdminOrderService.Delivered delivered = done(service.deliver(order.id()).join());

        assertEquals(0.0, delivered.refund());
        assertTrue(refunded.isEmpty());
        assertEquals(Map.of(alice, 90.0, bob, 45.0), paid);
        assertTrue(notices.isEmpty());
    }

    @Test
    void deliverHappensOnce() {
        var order = claimedWithDeliveries();

        done(service.deliver(order.id()).join());
        refused(Refusal.NOT_CLAIMED, service.deliver(order.id()).join());

        assertEquals(Map.of(owner(), 60.0), refunded);
        assertEquals(1, companyOutcomes().size());
    }

    @Test
    void deliverNeedsAnExistingCompany() {
        var order = orders.place(FEE);
        orders.state(order, OrderState.CLAIMED, COMPANY + 1);

        refused(Refusal.UNKNOWN_COMPANY, service.deliver(order.id()).join());

        assertEquals(OrderState.CLAIMED, orders.reload(order).state());
    }

    @Test
    void deliverRefusesOpenOrders() {
        var order = orders.place(FEE);

        refused(Refusal.NOT_CLAIMED, service.deliver(order.id()).join());

        assertTrue(paid.isEmpty());
        assertEquals(OrderState.UNCLAIMED, orders.reload(order).state());
    }

    @Test
    void extendOfClaimedOrderAddsDeliveryHours() {
        var order = claimedWithDeliveries();

        done(service.extend(order.id(), 12).join());

        assertEquals(order.deliveryBonus() + 12, orders.reload(order).deliveryBonus());
    }

    @Test
    void extendOfOpenOrderMovesTheRemovalWithoutUsingExtensions() {
        var order = orders.place(FEE);

        done(service.extend(order.id(), 48).join());

        var extended = orders.reload(order);
        assertEquals(order.claimed().plus(48, ChronoUnit.HOURS), extended.claimed());
        assertEquals(order.extensions(), extended.extensions());
    }

    @Test
    void extendRefusesDeliveredOrders() {
        var order = claimedWithDeliveries();
        orders.state(order, OrderState.DELIVERED, COMPANY);

        refused(Refusal.NOT_OPEN, service.extend(order.id(), 12).join());
    }

    private SimpleOrder soloClaimedByAlice() {
        var order = orders.place(FEE);
        assertTrue(orders.orderData().submitSoloClaim(alice, order, 1).join());
        orders.deliver(order, 0, Material.STONE, alice, 4);
        return orders.reload(order);
    }

    @Test
    void unclaimingASoloOrderTellsTheWorkerInsteadOfACompany() {
        var order = soloClaimedByAlice();

        AdminOrderService.Unclaimed unclaimed = done(service.unclaim(order.id()).join());

        assertEquals(Set.of(alice), unclaimed.workers());
        assertEquals(4, returned(alice));
        assertTrue(orders.reload(order).soloWorker().isEmpty());
        assertEquals(List.of("solo released", "owner reopened"), notices);
        assertEquals(0, SqLiteTestDatabase.count("order_history", "company_id IS NOT NULL"));
    }

    @Test
    void cancellingASoloOrderTellsTheWorker() {
        var order = soloClaimedByAlice();

        AdminOrderService.Cancelled cancelled =
                done(service.cancel(order.id(), true).join());

        assertTrue(cancelled.wasClaimed());
        assertEquals(4, returned(alice));
        assertEquals(List.of("solo released", "owner cancelled 155.0"), notices);
        assertEquals(List.of(HistoryOutcome.CANCELLED_BY_ADMIN), ownerOutcomes());
        assertEquals(0, SqLiteTestDatabase.count("order_history", "company_id IS NOT NULL"));
    }

    @Test
    void deliveringASoloOrderSettlesItWithTheSoloTax() {
        var order = soloClaimedByAlice();

        AdminOrderService.Delivered delivered = done(service.deliver(order.id()).join());

        assertEquals(Map.of(alice, 38.0), paid);
        assertEquals(110.0, delivered.refund());
        assertTrue(delivered.completion().payouts().get(0).solo());
        assertEquals(OrderState.DELIVERED, orders.reload(order).state());
        assertEquals(0, SqLiteTestDatabase.count("order_history", "company_id IS NOT NULL"));
    }

    private record RecordingNotices(List<String> log) implements AdminOrderService.Notices {
        @Override
        public void ownerReopened(SimpleOrder order) {
            log.add("owner reopened");
        }

        @Override
        public void companyUnclaimed(CompanyProfile company, SimpleOrder order) {
            log.add("company unclaimed " + company.id());
        }

        @Override
        public void ownerCancelled(SimpleOrder order, double refund) {
            log.add("owner cancelled " + refund);
        }

        @Override
        public void companyCancelled(CompanyProfile company, SimpleOrder order) {
            log.add("company cancelled " + company.id());
        }

        @Override
        public void ownerRefunded(SimpleOrder order, double refund) {
            log.add("owner refunded " + refund);
        }

        @Override
        public void soloReleased(UUID worker, SimpleOrder order) {
            log.add("solo released");
        }
    }
}
