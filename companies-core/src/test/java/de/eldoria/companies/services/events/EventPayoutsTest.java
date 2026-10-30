/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.order.ItemCriteria;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteHistoryData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteServerEventData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteStatsData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.event.EventKind;
import de.eldoria.companies.data.wrapper.event.EventPayout;
import de.eldoria.companies.data.wrapper.event.EventState;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.data.wrapper.stats.PayoutRecord;
import de.eldoria.companies.nodes.NodeMessage.ServerEventChanged;
import de.eldoria.companies.orders.HistoryRecorder;
import de.eldoria.companies.orders.OrderCompletionService;
import de.eldoria.companies.orders.OrderTable;
import de.eldoria.companies.util.TestExecutors;
import de.eldoria.companies.util.Times;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The server events in the payouts of completed orders: the order of {@link OrderTable#place} pays 100 for ten stone
 * and 50 for five dirt; the company's level 1 is taxed 10 %, solo orders 5 %.
 */
class EventPayoutsTest {
    private static final int COMPANY = 4;
    private static final double DELTA = 0.0001;
    private static final String BANK = "Bank";

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final Map<UUID, Double> paid = new ConcurrentHashMap<>();
    private final AtomicReference<Double> collected = new AtomicReference<>(0.0);
    private final AtomicInteger sharePercent = new AtomicInteger();
    private final AtomicLong kept = new AtomicLong();
    private final Map<String, Double> balances = new ConcurrentHashMap<>();
    private final List<ServerEventChanged> signals = new CopyOnWriteArrayList<>();

    @TempDir
    Path directory;

    private ExecutorService executor;
    private OrderTable orders;
    private SqLiteServerEventData events;
    private ServerEventService service;
    private ActiveEvents active;
    private OrderCompletionService completion;
    private double cap = 3.0;
    private EventPayouts payouts;
    private final List<String> withdrawals = new CopyOnWriteArrayList<>();
    private final List<String> deposits = new CopyOnWriteArrayList<>();
    private final Set<String> failingAccounts = ConcurrentHashMap.newKeySet();
    private volatile int refusals;
    private volatile Runnable onRefuse = () -> {};
    private volatile boolean throwOnPay;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(4);
        orders = OrderTable.create();
        events = new SqLiteServerEventData() {
            @Override
            public boolean pay(Map<Integer, Double> spend, List<EventPayout> payouts) {
                if (throwOnPay) throw new IllegalStateException("The database is gone");
                if (refusals > 0) {
                    refusals--;
                    onRefuse.run();
                    return false;
                }
                return super.pay(spend, payouts);
            }
        };
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        service = new ServerEventService(events, name -> name, signals::add, Times.UTC, logger);
        active = new ActiveEvents(events, Times.UTC);
        payouts = new EventPayouts(active, events, service, new FakeFunding(), () -> cap, logger);
        var treasury = new OrderCompletionService.Treasury() {
            @Override
            public void pay(OfflinePlayer worker, double amount) {
                paid.merge(worker.getUniqueId(), amount, Double::sum);
            }

            @Override
            public void collect(double amount) {
                collected.accumulateAndGet(amount, Double::sum);
            }

            @Override
            public int payoutShare(ISimpleCompany company) {
                return sharePercent.get();
            }

            @Override
            public void keep(ISimpleCompany company, int order, long cents) {
                kept.addAndGet(cents);
            }
        };
        completion = new OrderCompletionService(
                plugin,
                orders.orderData(),
                new SqLiteStatsData(executor),
                new HistoryRecorder(new SqLiteHistoryData(SqLiteTestDatabase.mapper(), executor), Times.UTC),
                treasury,
                level -> 10.0,
                () -> 5.0,
                Times.UTC,
                executor,
                order -> CompletableFuture.completedFuture(true),
                payouts);
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
        MockBukkit.unmock();
    }

    @Test
    void aTaxReductionLowersTheTaxOfEveryWorker() {
        start(event(EventKind.TAX_REDUCTION, 4, List.of(), List.of(), null, null, null));

        var done = complete();

        assertEquals(Map.of(alice, 94.0, bob, 47.0), paid);
        assertEquals(5.0 + 6.0 + 3.0, collected.get(), DELTA);
        assertTrue(done.payouts().stream().allMatch(payout -> payout.eventExtra() == 0.0));
        assertFalse(done.recorded());
    }

    @Test
    void reductionsAddUpAndTheTaxNeverDropsBelowZero() {
        start(event(EventKind.TAX_REDUCTION, 6, List.of(), List.of(), null, null, null));
        start(event(EventKind.TAX_REDUCTION, 6, List.of(), List.of(), null, null, null));

        complete();

        assertEquals(Map.of(alice, 100.0, bob, 50.0), paid);
        assertEquals(5.0, collected.get(), DELTA);
    }

    @Test
    void anItemScopedMultiplierPaysOnItsLinesToTheWorkerAloneAndOutsideTheTreasuryShare() {
        sharePercent.set(50);
        var id = start(
                event(EventKind.REWARD_MULTIPLIER, 1.5, List.of("STONE"), List.of(), SourceType.MINTED, null, 1000.0));

        var done = complete();

        assertEquals(45.0 + 50.0, paid.get(alice), DELTA);
        assertEquals(22.5, paid.get(bob), DELTA);
        assertEquals(4500 + 2250, kept.get());
        assertTrue(done.recorded());
        assertEquals(50.0, record(done, alice).eventExtra(), DELTA);
        assertEquals(0.0, record(done, bob).eventExtra(), DELTA);
        assertEquals(50.0, events.event(id).orElseThrow().spent(), DELTA);
        assertEquals(
                2,
                SqLiteTestDatabase.count(
                        "order_payouts", "order_id = " + done.order().id()));
        assertEquals(1, SqLiteTestDatabase.count("payout_events", "event_id = " + id + " AND amount = 50"));
        assertEquals(1, SqLiteTestDatabase.count("order_payouts", "event_extra = 50"));
    }

    @Test
    void multipliersMultiplyUpToTheCap() {
        cap = 3.0;
        var first = start(event(EventKind.REWARD_MULTIPLIER, 2, List.of(), List.of(), SourceType.MINTED, null, 1000.0));
        var second =
                start(event(EventKind.REWARD_MULTIPLIER, 2, List.of(), List.of(), SourceType.MINTED, null, 1000.0));

        complete();

        assertEquals(90.0 + 200.0, paid.get(alice), DELTA);
        assertEquals(45.0 + 100.0, paid.get(bob), DELTA);
        assertEquals(150.0, events.event(first).orElseThrow().spent(), DELTA);
        assertEquals(150.0, events.event(second).orElseThrow().spent(), DELTA);
    }

    @Test
    void anEventThatRunsOutPaysWhatIsLeftAndIsDepleted() {
        var id = start(event(EventKind.REWARD_MULTIPLIER, 2, List.of(), List.of(), SourceType.MINTED, null, 60.0));

        var done = complete();

        var extras =
                done.payouts().stream().mapToDouble(PayoutRecord::eventExtra).sum();
        assertEquals(60.0, extras, DELTA);
        assertEquals(
                90.0 + 45.0 + 60.0,
                paid.values().stream().mapToDouble(Double::doubleValue).sum(),
                DELTA);
        var event = events.event(id).orElseThrow();
        assertEquals(EventState.DEPLETED, event.state());
        assertEquals(60.0, event.spent(), DELTA);
        assertTrue(signals.contains(new ServerEventChanged(id, "DEPLETED")));
    }

    @Test
    void anAccountPaysNoMoreThanItHolds() {
        balances.put(BANK, 30.0);
        var id = start(event(EventKind.REWARD_MULTIPLIER, 2, List.of(), List.of(), SourceType.ACCOUNT, BANK, null));

        var done = complete();

        assertEquals(
                30.0,
                done.payouts().stream().mapToDouble(PayoutRecord::eventExtra).sum(),
                DELTA);
        assertEquals(0.0, balances.get(BANK), DELTA);
        assertEquals(EventState.DEPLETED, events.event(id).orElseThrow().state());
    }

    @Test
    void anAccountThatCanPayItsPartIsCharged() {
        balances.put(BANK, 1000.0);
        var id = start(event(EventKind.REWARD_MULTIPLIER, 1.1, List.of(), List.of(), SourceType.ACCOUNT, BANK, 500.0));

        complete();

        assertEquals(1000.0 - 15.0, balances.get(BANK), DELTA);
        assertEquals(15.0, events.event(id).orElseThrow().spent(), DELTA);
        assertEquals(EventState.ACTIVE, events.event(id).orElseThrow().state());
    }

    @Test
    void aLevelScopeMissesOtherLevelsAndSoloOrders() {
        start(event(EventKind.TAX_REDUCTION, 10, List.of(), List.of(2), null, null, null));

        complete();
        assertEquals(Map.of(alice, 90.0, bob, 45.0), paid);

        paid.clear();
        completion.completeSolo(deliveredByAliceAlone()).join().orElseThrow();
        assertEquals(Map.of(alice, 142.5), paid);
    }

    @Test
    void aSoloOrderGetsTheEventsWithoutLevelScope() {
        start(event(EventKind.TAX_REDUCTION, 5, List.of("DIRT"), List.of(), null, null, null));
        start(event(EventKind.REWARD_MULTIPLIER, 2, List.of("DIRT"), List.of(), SourceType.MINTED, null, 100.0));

        completion.completeSolo(deliveredByAliceAlone()).join().orElseThrow();

        assertEquals(150.0 + 50.0, paid.get(alice), DELTA);
    }

    @Test
    void anEventThatEndedPaysNothing() {
        var id = start(event(EventKind.REWARD_MULTIPLIER, 2, List.of(), List.of(), SourceType.MINTED, null, 100.0));
        active.activeAt(LocalDateTime.now(Clock.systemUTC()));
        events.state(id, EventState.ACTIVE, EventState.ENDED, "Nora", LocalDateTime.now(Clock.systemUTC()));

        var done = complete();

        assertEquals(Map.of(alice, 90.0, bob, 45.0), paid);
        assertFalse(done.recorded());
        assertEquals(0.0, events.event(id).orElseThrow().spent(), DELTA);
    }

    @Test
    void aRefusedPayoutGivesTheMoneyBackAndPaysFromTheNewBalance() {
        balances.put(BANK, 100.0);
        refusals = 1;
        onRefuse = () -> balances.put(BANK, -70.0);
        var id = start(event(EventKind.REWARD_MULTIPLIER, 2, List.of(), List.of(), SourceType.ACCOUNT, BANK, null));

        var done = complete();

        assertEquals(List.of(BANK + ":100.0"), deposits);
        assertEquals(
                30.0,
                done.payouts().stream().mapToDouble(PayoutRecord::eventExtra).sum(),
                DELTA);
        assertEquals(0.0, balances.get(BANK), DELTA);
        assertEquals(30.0, events.event(id).orElseThrow().spent(), DELTA);
        assertTrue(done.recorded());
    }

    @Test
    void aPayoutRefusedEveryTimeGivesUpAndPaysNoExtra() {
        balances.put(BANK, 1000.0);
        refusals = EventPayouts.ATTEMPTS;
        var id = start(event(EventKind.REWARD_MULTIPLIER, 1.1, List.of(), List.of(), SourceType.ACCOUNT, BANK, null));

        var done = complete();

        assertEquals(EventPayouts.ATTEMPTS, deposits.size());
        assertEquals(1000.0, balances.get(BANK), DELTA);
        assertEquals(Map.of(alice, 90.0, bob, 45.0), paid);
        assertFalse(done.recorded());
        assertEquals(0.0, events.event(id).orElseThrow().spent(), DELTA);
        assertEquals(
                2,
                SqLiteTestDatabase.count(
                        "order_payouts", "order_id = " + done.order().id() + " AND event_extra = 0"));
    }

    @Test
    void aWithdrawalThatFailsGivesBackWhatOtherAccountsPaid() {
        balances.put("Harbour", 1000.0);
        balances.put(BANK, 1000.0);
        failingAccounts.add(BANK);
        start(event(EventKind.REWARD_MULTIPLIER, 1.1, List.of(), List.of(), SourceType.ACCOUNT, "Harbour", null));
        start(event(EventKind.REWARD_MULTIPLIER, 1.1, List.of(), List.of(), SourceType.ACCOUNT, BANK, null));

        var done = complete();

        assertEquals(1000.0, balances.get("Harbour"), DELTA);
        assertEquals(1, deposits.size());
        assertTrue(deposits.getFirst().startsWith("Harbour:"));
        assertEquals(Map.of(alice, 90.0, bob, 45.0), paid);
        assertEquals(
                2,
                SqLiteTestDatabase.count(
                        "order_payouts", "order_id = " + done.order().id() + " AND event_extra = 0"));
    }

    @Test
    void aPayoutThatFailsGivesTheMoneyBack() {
        balances.put(BANK, 1000.0);
        throwOnPay = true;
        start(event(EventKind.REWARD_MULTIPLIER, 1.1, List.of(), List.of(), SourceType.ACCOUNT, BANK, null));

        var done = complete();

        assertEquals(List.of(BANK + ":15.0"), deposits);
        assertEquals(1000.0, balances.get(BANK), DELTA);
        assertEquals(Map.of(alice, 90.0, bob, 45.0), paid);
        assertFalse(done.recorded());
    }

    @Test
    void twoEventsOfOneAccountShareItsBalance() {
        balances.put(BANK, 120.0);
        var first =
                start(event(EventKind.REWARD_MULTIPLIER, 1.5, List.of(), List.of(), SourceType.ACCOUNT, BANK, null));
        var second =
                start(event(EventKind.REWARD_MULTIPLIER, 1.5, List.of(), List.of(), SourceType.ACCOUNT, BANK, null));

        var done = complete();

        assertEquals(
                120.0,
                done.payouts().stream().mapToDouble(PayoutRecord::eventExtra).sum(),
                DELTA);
        assertEquals(0.0, balances.get(BANK), DELTA);
        var spent = events.event(first).orElseThrow().spent()
                + events.event(second).orElseThrow().spent();
        assertEquals(120.0, spent, DELTA);
        assertTrue(signals.stream().anyMatch(signal -> signal.change().equals("DEPLETED")));
    }

    @Test
    void anItemScopedMultiplierPaysOnTheDeliveredPartOfACriteriaLineOnly() {
        start(event(EventKind.REWARD_MULTIPLIER, 2, List.of("BIRCH_LOG"), List.of(), SourceType.MINTED, null, 1000.0));
        start(event(EventKind.TAX_REDUCTION, 10, List.of("SPRUCE_LOG"), List.of(), null, null, null));
        var logs = ItemCriteria.of("BIRCH_LOG", "SPRUCE_LOG");
        var owner = orders.owner();
        orders.orderData()
                .submitOrder(
                        owner,
                        new SimpleOrder(owner.getUniqueId(), "logs", 0.0)
                                .toFullOrder(List.of(new OrderContent(
                                        0, new ItemStack(Material.BIRCH_LOG), false, logs, 10, 100.0))))
                .join();
        var placed = orders.orderData()
                .retrieveOrdersByPlayer(owner, OrderState.UNCLAIMED, OrderState.RECEIVED)
                .join()
                .getLast();
        orders.state(placed, OrderState.CLAIMED, COMPANY);
        orders.deliver(placed, 0, Material.BIRCH_LOG, alice, 4);
        orders.deliver(placed, 0, Material.SPRUCE_LOG, alice, 6);
        var profile = new CompanyProfile(COMPANY, "Acme", Times.now(), 1, CompanyDetails.NONE, List.of());

        var done = completion.complete(orders.reload(placed), profile).join().orElseThrow();

        assertEquals(40.0, record(done, alice).eventExtra(), DELTA);
        assertEquals(100.0 + 40.0, paid.get(alice), DELTA);
    }

    @Test
    void aStoppedPluginWithdrawsNothingAndPaysNoExtra() {
        balances.put(BANK, 1000.0);
        var id = start(event(EventKind.REWARD_MULTIPLIER, 2, List.of(), List.of(), SourceType.ACCOUNT, BANK, null));
        payouts.stop();

        var done = complete();

        assertTrue(withdrawals.isEmpty());
        assertTrue(deposits.isEmpty());
        assertEquals(1000.0, balances.get(BANK), DELTA);
        assertEquals(Map.of(alice, 90.0, bob, 45.0), paid);
        assertFalse(done.recorded());
        assertEquals(0.0, events.event(id).orElseThrow().spent(), DELTA);
    }

    private OrderCompletionService.Completion complete() {
        var order = orders.place(5.0);
        orders.state(order, OrderState.CLAIMED, COMPANY);
        orders.deliver(order, 0, Material.STONE, alice, 10);
        orders.deliver(order, 1, Material.DIRT, bob, 5);
        var profile = new CompanyProfile(COMPANY, "Acme", Times.now(), 1, CompanyDetails.NONE, List.of());
        return completion.complete(orders.reload(order), profile).join().orElseThrow();
    }

    private SimpleOrder deliveredByAliceAlone() {
        var order = orders.place(5.0);
        assertTrue(orders.orderData().submitSoloClaim(alice, order, 1).join());
        orders.deliver(order, 0, Material.STONE, alice, 10);
        orders.deliver(order, 1, Material.DIRT, alice, 5);
        return orders.reload(order);
    }

    private static PayoutRecord record(OrderCompletionService.Completion done, UUID worker) {
        return done.payouts().stream()
                .filter(payout -> payout.worker().equals(worker))
                .findFirst()
                .orElseThrow();
    }

    private int start(ServerEvent draft) {
        var created = service.create(draft);
        assertTrue(created.stored(), String.valueOf(created.problem()));
        service.run();
        active.invalidate();
        return created.id();
    }

    private static ServerEvent event(
            EventKind kind,
            double value,
            List<String> materials,
            List<Integer> levels,
            @Nullable SourceType funding,
            @Nullable String account,
            @Nullable Double budget) {
        var now = LocalDateTime.now(Clock.systemUTC());
        return ServerEvent.draft(
                "Event",
                kind,
                value,
                materials,
                levels,
                now.minusHours(1),
                now.plusHours(1),
                funding,
                account,
                budget,
                null,
                "Nora",
                now);
    }

    private final class FakeFunding implements EventPayouts.Funding {
        @Override
        public double balance(String account) {
            return balances.getOrDefault(account, 0.0);
        }

        @Override
        public boolean withdraw(String account, double amount) {
            if (failingAccounts.contains(account)) throw new IllegalStateException("The economy is gone");
            withdrawals.add(account + ":" + amount);
            var balance = balances.getOrDefault(account, 0.0);
            if (balance + DELTA < amount) return false;
            balances.put(account, balance - amount);
            return true;
        }

        @Override
        public void deposit(String account, double amount) {
            deposits.add(account + ":" + amount);
            balances.merge(account, amount, Double::sum);
        }
    }
}
