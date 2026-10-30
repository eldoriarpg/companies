/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.data.wrapper.event.EventKind;
import de.eldoria.companies.data.wrapper.event.EventPayout;
import de.eldoria.companies.data.wrapper.event.EventState;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.data.wrapper.stats.PayoutRecord;
import de.eldoria.companies.orders.OrderCompletionService;
import de.eldoria.companies.services.events.ActiveEvents;
import de.eldoria.companies.services.events.EventEffects;
import de.eldoria.companies.services.events.EventPayouts;
import de.eldoria.companies.services.events.ServerEventService;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

import javax.sql.DataSource;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What every dialect does with the table of server events. The database is started once per dialect and emptied
 * before each test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ServerEventDataContract {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 12, 0);
    private static final double DELTA = 0.0001;

    private DataSource dataSource;
    private AServerEventData events;

    /**
     * Starts a migrated database.
     *
     * @return its data source
     */
    protected abstract DataSource start() throws Exception;

    /**
     * Stops the database.
     */
    protected abstract void stop();

    /**
     * @return the dialect's event table
     */
    protected abstract AServerEventData events();

    @BeforeAll
    void startDatabase() throws Exception {
        dataSource = start();
    }

    @BeforeEach
    void setUp() {
        QueryConfiguration.setDefault(QueryConfiguration.builder(dataSource).build());
        query("DELETE FROM payout_events").single().delete();
        query("DELETE FROM order_payouts").single().delete();
        query("DELETE FROM server_events").single().delete();
        events = events();
    }

    @AfterAll
    void stopDatabase() {
        if (dataSource != null) stop();
    }

    @Test
    void anEventIsStoredAndReadWithItsScopesAndFunding() {
        var id = events.create(ServerEvent.draft(
                        "Harvest week",
                        EventKind.REWARD_MULTIPLIER,
                        1.5,
                        List.of("WHEAT", "CARROT"),
                        List.of(2, 3),
                        NOW.plusHours(1),
                        NOW.plusDays(2),
                        SourceType.ACCOUNT,
                        "Bank",
                        500.0,
                        "harvest",
                        "Nora",
                        NOW))
                .orElseThrow();

        var read = events.event(id).orElseThrow();
        assertEquals(id, read.id());
        assertEquals("Harvest week", read.name());
        assertEquals(EventKind.REWARD_MULTIPLIER, read.kind());
        assertEquals(1.5, read.value());
        assertEquals(List.of("WHEAT", "CARROT"), read.materials());
        assertEquals(List.of(2, 3), read.levels());
        assertEquals(NOW.plusHours(1), read.starts());
        assertEquals(NOW.plusDays(2), read.ends());
        assertEquals(SourceType.ACCOUNT, read.funding());
        assertEquals("Bank", read.account());
        assertEquals(500.0, read.budget());
        assertEquals(0.0, read.spent());
        assertEquals(EventState.SCHEDULED, read.state());
        assertEquals("harvest", read.template());
        assertEquals("Nora", read.createdBy());
        assertEquals(NOW, read.created());
        assertNull(read.closed());
        assertNull(read.closedBy());
    }

    @Test
    void anEventWithoutScopesOrFundingReadsEmptyListsAndNulls() {
        var id = events.create(tax("Tax holiday", NOW, NOW.plusDays(1))).orElseThrow();

        var read = events.event(id).orElseThrow();
        assertTrue(read.materials().isEmpty());
        assertTrue(read.levels().isEmpty());
        assertNull(read.funding());
        assertNull(read.account());
        assertNull(read.budget());
        assertNull(read.template());
        assertTrue(events.event(id + 100).isEmpty());
    }

    @Test
    void liveHoldsWaitingAndRunningEventsSoonestStartFirst() {
        var later =
                events.create(tax("Later", NOW.plusDays(2), NOW.plusDays(3))).orElseThrow();
        var running = events.create(tax("Running", NOW.minusHours(1), NOW.plusDays(1)))
                .orElseThrow();
        var ended =
                events.create(tax("Ended", NOW.minusDays(2), NOW.minusDays(1))).orElseThrow();
        assertTrue(events.state(running, EventState.SCHEDULED, EventState.ACTIVE, null, NOW));
        assertTrue(events.state(ended, EventState.SCHEDULED, EventState.ENDED, null, NOW));

        assertEquals(
                List.of(running, later),
                events.live().stream().map(ServerEvent::id).toList());
        assertEquals(
                List.of(ended, running, later),
                events.events(10).stream().map(ServerEvent::id).toList());
        assertEquals(
                List.of(ended), events.events(1).stream().map(ServerEvent::id).toList());
    }

    @Test
    void aStateMovesOnlyFromTheStateItIsInAndAFinalOneNotesTheClose() {
        var id = events.create(tax("Tax holiday", NOW, NOW.plusDays(1))).orElseThrow();

        assertFalse(events.state(id, EventState.ACTIVE, EventState.ENDED, "Nora", NOW));
        assertTrue(events.state(id, EventState.SCHEDULED, EventState.ACTIVE, "Nora", NOW));
        var active = events.event(id).orElseThrow();
        assertEquals(EventState.ACTIVE, active.state());
        assertNull(active.closed());
        assertNull(active.closedBy());

        assertTrue(events.state(id, EventState.ACTIVE, EventState.CANCELLED, "Nora", NOW.plusHours(2)));
        assertFalse(events.state(id, EventState.ACTIVE, EventState.ENDED, null, NOW.plusHours(3)));
        var cancelled = events.event(id).orElseThrow();
        assertEquals(EventState.CANCELLED, cancelled.state());
        assertEquals(NOW.plusHours(2), cancelled.closed());
        assertEquals("Nora", cancelled.closedBy());
    }

    @Test
    void twoNodesMovingTheSameEventAtOnceMoveItOnce() throws Exception {
        var id = events.create(tax("Tax holiday", NOW, NOW.plusDays(1))).orElseThrow();
        var ready = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(4)) {
            var moves = List.of(
                    CompletableFuture.supplyAsync(
                            () -> await(
                                    ready, () -> events.state(id, EventState.SCHEDULED, EventState.ACTIVE, null, NOW)),
                            pool),
                    CompletableFuture.supplyAsync(
                            () -> await(
                                    ready,
                                    () -> events.state(id, EventState.SCHEDULED, EventState.CANCELLED, "Nora", NOW)),
                            pool),
                    CompletableFuture.supplyAsync(
                            () -> await(
                                    ready, () -> events.state(id, EventState.SCHEDULED, EventState.ACTIVE, null, NOW)),
                            pool));
            ready.countDown();
            var won = moves.stream()
                    .map(CompletableFuture::join)
                    .filter(Boolean::booleanValue)
                    .count();
            assertEquals(1, won);
        }
        assertTrue(events.event(id).orElseThrow().state() != EventState.SCHEDULED);
    }

    @Test
    void anEventEndsAtItsEndOnlyWhileItHasTheEndThatWasRead() {
        var id = events.create(tax("Tax holiday", NOW, NOW.plusDays(1))).orElseThrow();
        events.state(id, EventState.SCHEDULED, EventState.ACTIVE, null, NOW);
        assertTrue(events.ends(id, NOW.plusDays(1), NOW.plusDays(2)));

        assertFalse(events.expire(id, EventState.ACTIVE, NOW.plusDays(1), null, NOW.plusDays(1)));
        assertFalse(events.expire(id, EventState.SCHEDULED, NOW.plusDays(2), null, NOW.plusDays(2)));
        assertEquals(EventState.ACTIVE, events.event(id).orElseThrow().state());

        assertTrue(events.expire(id, EventState.ACTIVE, NOW.plusDays(2), null, NOW.plusDays(2)));
        assertFalse(events.expire(id, EventState.ACTIVE, NOW.plusDays(2), null, NOW.plusDays(2)));
        var ended = events.event(id).orElseThrow();
        assertEquals(EventState.ENDED, ended.state());
        assertEquals(NOW.plusDays(2), ended.closed());
        assertNull(ended.closedBy());
    }

    @Test
    void aMissedEventNotesTheMarkerAsWhoClosedIt() {
        var id = events.create(tax("Tax holiday", NOW, NOW.plusDays(1))).orElseThrow();

        assertTrue(events.expire(id, EventState.SCHEDULED, NOW.plusDays(1), ServerEvent.MISSED_BY, NOW.plusDays(2)));

        var missed = events.event(id).orElseThrow();
        assertEquals(EventState.ENDED, missed.state());
        assertEquals(ServerEvent.MISSED_BY, missed.closedBy());
        assertEquals(NOW.plusDays(2), missed.closed());
    }

    @Test
    void theEndMovesOnlyFromTheEndItHasWhileTheEventRuns() {
        var id = events.create(tax("Tax holiday", NOW, NOW.plusDays(1))).orElseThrow();

        assertFalse(events.ends(id, NOW.plusHours(5), NOW.plusDays(2)));
        assertFalse(events.ends(id, NOW.plusDays(1), NOW.minusHours(1)));
        assertTrue(events.ends(id, NOW.plusDays(1), NOW.plusDays(2)));
        assertFalse(events.ends(id, NOW.plusDays(1), NOW.plusDays(3)));
        assertEquals(NOW.plusDays(2), events.event(id).orElseThrow().ends());

        events.state(id, EventState.SCHEDULED, EventState.CANCELLED, "Nora", NOW);
        assertFalse(events.ends(id, NOW.plusDays(2), NOW.plusDays(3)));
    }

    @Test
    void aBudgetIsNeverSetBelowWhatWasSpentNorRemovedFromAMintedEvent() {
        var account =
                events.create(multiplier(SourceType.ACCOUNT, "Bank", 100.0)).orElseThrow();
        var minted = events.create(multiplier(SourceType.MINTED, null, 100.0)).orElseThrow();
        var tax = events.create(tax("Tax holiday", NOW, NOW.plusDays(1))).orElseThrow();
        query("UPDATE server_events SET spent = ? WHERE id = ?")
                .single(call().bind(40.0).bind(account))
                .update();

        assertFalse(events.budget(account, 30.0));
        assertTrue(events.budget(account, 40.0));
        assertTrue(events.budget(account, 250.0));
        assertEquals(250.0, events.event(account).orElseThrow().budget());
        assertTrue(events.budget(account, null));
        assertNull(events.event(account).orElseThrow().budget());

        assertFalse(events.budget(minted, null));
        assertTrue(events.budget(minted, 80.0));
        assertEquals(80.0, events.event(minted).orElseThrow().budget());

        assertFalse(events.budget(tax, 10.0));
        assertNull(events.event(tax).orElseThrow().budget());

        events.state(minted, EventState.SCHEDULED, EventState.CANCELLED, "Nora", NOW);
        assertFalse(events.budget(minted, 90.0));
    }

    @Test
    void aPayoutIsWrittenTogetherWithTheMoneyItsEventsSpent() {
        var id = activeMultiplier(100.0);

        assertTrue(events.pay(Map.of(id, 40.0), List.of(payout(UUID.randomUUID(), 40.0, Map.of(id, 40.0)))));

        assertEquals(40.0, events.event(id).orElseThrow().spent(), DELTA);
        assertEquals(1, count("order_payouts", "event_extra > 39.99"));
        assertEquals(1, count("payout_events", "amount > 39.99"));
    }

    @Test
    void aPayoutBeyondTheBudgetOrOfAnEventThatIsNotActiveWritesNothing() {
        var id = activeMultiplier(100.0);
        var other = activeMultiplier(100.0);
        assertTrue(events.pay(Map.of(id, 80.0), List.of()));

        assertFalse(events.pay(
                orderedMap(other, 10.0, id, 30.0),
                List.of(payout(UUID.randomUUID(), 40.0, orderedMap(other, 10.0, id, 30.0)))));
        assertEquals(80.0, events.event(id).orElseThrow().spent(), DELTA);
        assertEquals(0.0, events.event(other).orElseThrow().spent(), DELTA);
        assertEquals(0, count("order_payouts", "1 = 1"));
        assertEquals(0, count("payout_events", "1 = 1"));

        assertTrue(events.pay(Map.of(id, 20.0), List.of()));
        events.state(other, EventState.ACTIVE, EventState.ENDED, null, NOW);
        assertFalse(events.pay(Map.of(other, 1.0), List.of()));
        assertEquals(100.0, events.event(id).orElseThrow().spent(), DELTA);
    }

    @Test
    void twoNodesPayingTheLastOfABudgetAtOnceNeverSpendMoreThanIt() throws Exception {
        var id = activeMultiplier(100.0);
        var barrier = new CyclicBarrier(2);
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        var clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        var event = events.event(id).orElseThrow();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var nodes = List.of(node(barrier, clock, logger), node(barrier, clock, logger));
            var futures = nodes.stream()
                    .map(node -> CompletableFuture.supplyAsync(
                            () -> {
                                var worker = UUID.randomUUID();
                                return node.fund(
                                        List.of(event),
                                        List.of(new OrderCompletionService.Share(
                                                worker, List.of(new EventEffects.Line("STONE", 100.0)))),
                                        List.of(new PayoutRecord(7, 0, worker, 10, 100.0, 0.0, 0.0, 100.0, true, NOW)));
                            },
                            pool))
                    .toList();
            var extras = futures.stream()
                    .map(CompletableFuture::join)
                    .mapToDouble(funded -> funded.extras().values().stream()
                            .mapToDouble(Double::doubleValue)
                            .sum())
                    .sum();
            assertEquals(100.0, extras, DELTA);
        }
        var spent = events.event(id).orElseThrow();
        assertEquals(100.0, spent.spent(), DELTA);
        assertEquals(EventState.DEPLETED, spent.state());
        assertEquals(1, count("order_payouts", "event_extra > 99.99"));
    }

    private EventPayouts node(CyclicBarrier barrier, Clock clock, Logger logger) {
        var data = new Racing(events(), barrier);
        var service = new ServerEventService(data, name -> name, signal -> {}, clock, logger);
        return new EventPayouts(
                new ActiveEvents(data, clock),
                data,
                service,
                new EventPayouts.Funding() {
                    @Override
                    public double balance(String account) {
                        return 0;
                    }

                    @Override
                    public boolean withdraw(String account, double amount) {
                        return false;
                    }

                    @Override
                    public void deposit(String account, double amount) {}
                },
                () -> 3.0,
                logger);
    }

    private int activeMultiplier(double budget) {
        var id = events.create(multiplier(SourceType.MINTED, null, budget)).orElseThrow();
        assertTrue(events.state(id, EventState.SCHEDULED, EventState.ACTIVE, null, NOW));
        return id;
    }

    private static EventPayout payout(UUID worker, double extra, Map<Integer, Double> byEvent) {
        return new EventPayout(new PayoutRecord(7, 0, worker, 10, 100.0, 0.0, 0.0, 100.0, true, NOW, extra), byEvent);
    }

    private static Map<Integer, Double> orderedMap(int first, double firstAmount, int second, double secondAmount) {
        var map = new LinkedHashMap<Integer, Double>();
        map.put(first, firstAmount);
        map.put(second, secondAmount);
        return map;
    }

    private static int count(String table, String where) {
        return query("SELECT COUNT(*) AS count FROM " + table + " WHERE " + where)
                .single()
                .map(row -> row.getInt("count"))
                .first()
                .orElse(0);
    }

    /**
     * The table of one node whose first payout waits until the other node read the events too, so both pay from the
     * same budget.
     */
    private static final class Racing extends AServerEventData {
        private final AServerEventData delegate;
        private final CyclicBarrier barrier;
        private boolean waited;

        private Racing(AServerEventData delegate, CyclicBarrier barrier) {
            this.delegate = delegate;
            this.barrier = barrier;
        }

        @Override
        public Optional<Integer> create(ServerEvent event) {
            return delegate.create(event);
        }

        @Override
        public Optional<ServerEvent> event(int id) {
            return delegate.event(id);
        }

        @Override
        public List<ServerEvent> events(int limit) {
            return delegate.events(limit);
        }

        @Override
        public List<ServerEvent> live() {
            return delegate.live();
        }

        @Override
        public boolean state(int event, EventState from, EventState to, @Nullable String by, LocalDateTime at) {
            return delegate.state(event, from, to, by, at);
        }

        @Override
        public boolean expire(int event, EventState from, LocalDateTime ends, @Nullable String by, LocalDateTime at) {
            return delegate.expire(event, from, ends, by, at);
        }

        @Override
        public boolean ends(int event, LocalDateTime from, LocalDateTime to) {
            return delegate.ends(event, from, to);
        }

        @Override
        public boolean pay(Map<Integer, Double> spend, List<EventPayout> payouts) {
            if (!waited) {
                waited = true;
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (BrokenBarrierException | TimeoutException e) {
                    throw new IllegalStateException(e);
                }
            }
            return delegate.pay(spend, payouts);
        }

        @Override
        public boolean budget(int event, @Nullable Double budget) {
            return delegate.budget(event, budget);
        }
    }

    private static boolean await(CountDownLatch ready, BooleanSupplier move) {
        try {
            ready.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return move.getAsBoolean();
    }

    private static ServerEvent tax(String name, LocalDateTime starts, LocalDateTime ends) {
        return ServerEvent.draft(
                name,
                EventKind.TAX_REDUCTION,
                5,
                List.of(),
                List.of(),
                starts,
                ends,
                null,
                null,
                null,
                null,
                "Nora",
                NOW);
    }

    private static ServerEvent multiplier(SourceType funding, @Nullable String account, double budget) {
        return ServerEvent.draft(
                "Double pay",
                EventKind.REWARD_MULTIPLIER,
                2,
                List.of(),
                List.of(),
                NOW,
                NOW.plusDays(1),
                funding,
                account,
                budget,
                null,
                "Nora",
                NOW);
    }
}
