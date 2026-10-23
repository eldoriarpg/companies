/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.order.HistoryOutcome;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteHistoryData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteStatsData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.data.wrapper.stats.MemberStats;
import de.eldoria.companies.data.wrapper.stats.PayoutRecord;
import de.eldoria.companies.events.EventRecorder;
import de.eldoria.companies.events.order.OrderPaymentEvent;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderCompletionServiceTest {
    private static final int COMPANY = 4;
    private static final CompanyProfile PROFILE =
            new CompanyProfile(COMPANY, "Acme", LocalDateTime.now(), 1, CompanyDetails.NONE, List.of());

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final Map<UUID, Double> paid = new ConcurrentHashMap<>();
    private final AtomicReference<Double> collected = new AtomicReference<>(0.0);
    private final AtomicInteger sharePercent = new AtomicInteger();
    private final List<Kept> kept = new CopyOnWriteArrayList<>();

    @TempDir
    Path directory;

    private ExecutorService executor;
    private Plugin plugin;
    private OrderTable orders;
    private SqLiteStatsData stats;
    private SqLiteHistoryData history;
    private OrderCompletionService service;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(4);
        orders = OrderTable.create();
        stats = new SqLiteStatsData(executor);
        history = new SqLiteHistoryData(SqLiteTestDatabase.mapper(), executor);
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
                kept.add(new Kept(company.id(), order, cents));
            }
        };
        service = new OrderCompletionService(
                plugin,
                orders.orderData(),
                stats,
                new HistoryRecorder(history, Clock.systemDefaultZone()),
                treasury,
                level -> 10.0,
                Clock.systemDefaultZone(),
                executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        MockBukkit.unmock();
    }

    private SimpleOrder deliveredByAliceAndBob() {
        var order = orders.place(5.0);
        orders.state(order, OrderState.CLAIMED, COMPANY);
        orders.deliver(order, 0, Material.STONE, alice, 10);
        orders.deliver(order, 1, Material.DIRT, bob, 5);
        return orders.reload(order);
    }

    private Map<UUID, MemberStats> memberStats() {
        return stats.retrieveMemberStats(COMPANY, null).join().stream()
                .collect(Collectors.toMap(MemberStats::worker, member -> member));
    }

    @Test
    void ledgerHoldsThePayoutsOfTheCompletion() {
        var completion =
                service.complete(deliveredByAliceAndBob(), PROFILE).join().orElseThrow();

        var payouts = completion.payouts().stream()
                .sorted(Comparator.comparing(PayoutRecord::gross))
                .toList();
        assertEquals(2, payouts.size());
        assertEquals(bob, payouts.get(0).worker());
        assertEquals(5, payouts.get(0).items());
        assertEquals(50.0, payouts.get(0).gross());
        assertEquals(45.0, payouts.get(0).net());
        assertEquals(alice, payouts.get(1).worker());
        assertEquals(90.0, payouts.get(1).net());
        assertTrue(payouts.stream().allMatch(PayoutRecord::paid));

        var members = memberStats();
        assertEquals(new MemberStats(alice, 10, 90.0, 1), members.get(alice));
        assertEquals(new MemberStats(bob, 5, 45.0, 1), members.get(bob));
        assertEquals(Map.of(alice, 90.0, bob, 45.0), paid);
        assertEquals(20.0, collected.get(), 0.001);
        assertEquals(OrderState.DELIVERED, orders.reload(completion.order()).state());
    }

    @Test
    void cancelledPaymentIsWrittenAsNotPaid() {
        plugin.getServer().getPluginManager().registerEvents(new CancelPaymentsOf(bob), plugin);

        var completion =
                service.complete(deliveredByAliceAndBob(), PROFILE).join().orElseThrow();

        var unpaid = completion.payouts().stream()
                .filter(payout -> !payout.paid())
                .map(PayoutRecord::worker)
                .collect(Collectors.toSet());
        assertEquals(Set.of(bob), unpaid);
        assertEquals(Map.of(alice, 90.0), paid);
        assertEquals(new MemberStats(bob, 5, 0.0, 1), memberStats().get(bob));
        var snapshot =
                history.retrieveCompanyHistory(COMPANY, 1, 10).join().get(0).snapshot();
        assertEquals(90.0, snapshot.net());
    }

    @Test
    void historyEntryIsWrittenOnce() {
        var order = deliveredByAliceAndBob();

        assertTrue(service.complete(order, PROFILE).join().isPresent());
        assertFalse(service.complete(order, PROFILE).join().isPresent());

        var entries = history.retrieveCompanyHistory(COMPANY, 1, 10).join();
        assertEquals(1, entries.size());
        assertEquals(HistoryOutcome.DELIVERED, entries.get(0).outcome());
        assertEquals(135.0, entries.get(0).snapshot().net());
        assertTrue(history.retrieveOwnerHistory(orders.owner().getUniqueId(), 1, 10)
                .join()
                .isEmpty());
        assertEquals(2, SqLiteTestDatabase.count("order_payouts", "order_id = " + order.id()));
    }

    @Test
    void incompleteOrderIsNotCompleted() {
        var order = orders.place(0.0);
        orders.state(order, OrderState.CLAIMED, COMPANY);
        orders.deliver(order, 0, Material.STONE, alice, 3);

        assertTrue(service.complete(orders.reload(order), PROFILE).join().isEmpty());

        assertTrue(history.retrieveCompanyHistory(COMPANY, 1, 10).join().isEmpty());
        assertEquals(0, SqLiteTestDatabase.count("order_payouts", "order_id = " + order.id()));
    }

    @Test
    void paymentsCarryTheCompanyAndAreAsynchronous() {
        var events = EventRecorder.listen(plugin, OrderPaymentEvent.class);

        service.complete(deliveredByAliceAndBob(), PROFILE).join().orElseThrow();

        var payments = events.of(OrderPaymentEvent.class);
        assertEquals(2, payments.size());
        assertTrue(payments.stream().allMatch(payment -> payment.company() == PROFILE));
        assertTrue(payments.stream().allMatch(OrderPaymentEvent::isAsynchronous));
    }

    @Test
    void treasurySharesAreCreditedOncePerOrderAndWrittenToTheLedger() {
        sharePercent.set(10);
        var events = EventRecorder.listen(plugin, OrderPaymentEvent.class);

        var completion =
                service.complete(deliveredByAliceAndBob(), PROFILE).join().orElseThrow();

        var order = completion.order().id();
        assertEquals(List.of(new Kept(COMPANY, order, 1350)), kept);
        assertEquals(Map.of(alice, 81.0, bob, 40.5), paid);
        assertEquals(20.0, collected.get(), 0.001);
        var shares = completion.payouts().stream().collect(Collectors.toMap(PayoutRecord::worker, PayoutRecord::share));
        assertEquals(Map.of(alice, 9.0, bob, 4.5), shares);
        assertEquals(1, SqLiteTestDatabase.count("order_payouts", "order_id = " + order + " AND share = 9.0"));
        assertEquals(1, SqLiteTestDatabase.count("order_payouts", "order_id = " + order + " AND share = 4.5"));
        var payments = events.of(OrderPaymentEvent.class).stream()
                .collect(Collectors.toMap(payment -> payment.player().getUniqueId(), OrderPaymentEvent::share));
        assertEquals(Map.of(alice, 9.0, bob, 4.5), payments);
    }

    @Test
    void cancelledPaymentIsNotShared() {
        sharePercent.set(10);
        plugin.getServer().getPluginManager().registerEvents(new CancelPaymentsOf(bob), plugin);

        var completion =
                service.complete(deliveredByAliceAndBob(), PROFILE).join().orElseThrow();

        assertEquals(List.of(new Kept(COMPANY, completion.order().id(), 900)), kept);
        assertEquals(Map.of(alice, 81.0), paid);
    }

    @Test
    void withoutShareNothingIsCredited() {
        var completion =
                service.complete(deliveredByAliceAndBob(), PROFILE).join().orElseThrow();

        assertTrue(kept.isEmpty());
        assertTrue(completion.payouts().stream().allMatch(payout -> payout.share() == 0.0));
    }

    private record Kept(int company, int order, long cents) {}

    private record CancelPaymentsOf(UUID worker) implements Listener {
        @EventHandler
        public void onPayment(OrderPaymentEvent event) {
            if (event.player().getUniqueId().equals(worker)) event.setCancelled(true);
        }
    }
}
