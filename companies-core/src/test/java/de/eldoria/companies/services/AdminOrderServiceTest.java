/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.components.order.HistoryOutcome;
import de.eldoria.companies.components.order.OrderState;
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
import de.eldoria.companies.orders.HistoryRecorder;
import de.eldoria.companies.orders.OrderCompletionService;
import de.eldoria.companies.orders.OrderTable;
import de.eldoria.companies.services.AdminOrderService.Refusal;
import de.eldoria.companies.services.AdminOrderService.Result;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

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

    @TempDir
    Path directory;

    private ExecutorService executor;
    private OrderTable orders;
    private SqLiteHistoryData history;
    private AdminOrderService service;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(8);
        orders = OrderTable.create();
        history = new SqLiteHistoryData(SqLiteTestDatabase.mapper(), executor);
        var recorder = new HistoryRecorder(history, Clock.systemDefaultZone());
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
        var completion = new OrderCompletionService(
                plugin,
                orders.orderData(),
                new SqLiteStatsData(executor),
                recorder,
                treasury,
                level -> 10.0,
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
        service = new AdminOrderService(
                orders.orderData(),
                id -> id == COMPANY ? Optional.of(PROFILE) : Optional.empty(),
                completion,
                recorder,
                funds,
                new RecordingNotices(notices),
                Logger.getAnonymousLogger(),
                executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
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
    void cancelWithoutRefundCollectsTheFee() {
        var order = orders.full(orders.place(FEE));

        var money = AdminOrderService.cancelMoney(order, false);

        assertEquals(0.0, money.refund());
        assertEquals(FEE, money.collected());
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
        assertEquals(List.of("company unclaimed " + COMPANY, "owner reopened", "returned 2"), notices);
        assertTrue(refunded.isEmpty());
        assertTrue(paid.isEmpty());
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
        assertEquals(List.of("company cancelled " + COMPANY, "owner cancelled 155.0", "returned 2"), notices);
    }

    @Test
    void cancelOfOpenOrderWithoutRefundCollectsTheFee() {
        var order = orders.place(FEE);

        AdminOrderService.Cancelled cancelled =
                done(service.cancel(order.id(), false).join());

        assertFalse(cancelled.wasClaimed());
        assertTrue(refunded.isEmpty());
        assertEquals(FEE, collected.get());
        assertEquals(List.of(HistoryOutcome.CANCELLED_BY_ADMIN), ownerOutcomes());
        assertTrue(companyOutcomes().isEmpty());
        assertEquals(List.of("owner cancelled 0.0", "returned 0"), notices);
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
        public void itemsReturned(Set<UUID> workers) {
            log.add("returned " + workers.size());
        }
    }
}
