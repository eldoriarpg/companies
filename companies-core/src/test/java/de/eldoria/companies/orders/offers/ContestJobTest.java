/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.offers;

import de.eldoria.companies.components.event.EventKind;
import de.eldoria.companies.components.event.EventState;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.dashboard.MutableClock;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOfferData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.data.wrapper.offer.ContestEntry;
import de.eldoria.companies.data.wrapper.offer.ContestPrize;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferLine;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.data.wrapper.offer.PrizeState;
import de.eldoria.companies.orders.system.SystemOrderService;
import de.eldoria.companies.util.Times;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContestJobTest {
    private static final String BANK = "Festival fund";

    @TempDir
    Path directory;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-29T08:00:00Z"));
    private final Map<String, Double> balances = new HashMap<>();
    private final List<String> prizesPaid = new ArrayList<>();
    private final List<Integer> announced = new ArrayList<>();
    private final List<List<Integer>> rankings = new ArrayList<>();
    private final List<Integer> cancelled = new ArrayList<>();
    private final List<Integer> contestsStarted = new ArrayList<>();
    private final List<List<String>> paidPrizes = new ArrayList<>();
    private Integer failingWinner;
    private boolean failOutcome;
    private boolean crashOnClaim;
    private boolean refuseClaim;
    private int iron;
    private int stone;
    private int wood;
    private SqLiteOfferData offers;
    private EventOfferService service;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        Times.useZone(() -> ZoneOffset.UTC);
        SqLiteTestDatabase.create(directory);
        offers = new SqLiteOfferData(SqLiteTestDatabase.mapper()) {
            @Override
            public boolean claimPrize(int offer, int place) {
                if (crashOnClaim) {
                    crashOnClaim = false;
                    throw new IllegalStateException("the server stopped");
                }
                if (refuseClaim) {
                    refuseClaim = false;
                    return false;
                }
                return super.claimPrize(offer, place);
            }

            @Override
            public boolean prizeOutcome(int offer, int place, boolean paid) {
                if (failOutcome) throw new IllegalStateException("could not write the outcome");
                return super.prizeOutcome(offer, place, paid);
            }
        };
        iron = company("Iron Works");
        stone = company("Stone Cutters");
        wood = company("Wood Works");
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        service = new EventOfferService(
                offers,
                new SqLiteOrderData(null, SqLiteTestDatabase.mapper()),
                new NoPlacement(),
                new FakeFunding(),
                (company, offer, amount) -> {
                    if (failingWinner != null && failingWinner == company) return false;
                    prizesPaid.add(company + ":" + amount);
                    return true;
                },
                new EventOfferService.Announcer() {
                    @Override
                    public void reached(EventOffer offer) {}

                    @Override
                    public void started(EventOffer offer) {
                        contestsStarted.add(offer.id());
                    }

                    @Override
                    public void contestClosed(EventOffer offer, List<ContestEntry> ranking, List<ContestPrize> prizes) {
                        announced.add(offer.id());
                        rankings.add(ranking.stream().map(ContestEntry::company).toList());
                        paidPrizes.add(prizes.stream()
                                .filter(ContestPrize::paid)
                                .map(prize -> prize.place() + ":" + prize.company())
                                .toList());
                    }

                    @Override
                    public void contestCancelled(EventOffer offer) {
                        cancelled.add(offer.id());
                    }
                },
                clock,
                () -> 0.5,
                logger);
    }

    @AfterEach
    void tearDown() {
        Times.useZone(Times::machineZone);
        MockBukkit.unmock();
    }

    @Test
    void anAccountPaysTheWholeBudgetWhenTheContestStarts() {
        balances.put(BANK, 1000.0);
        var id = contest(100, 2.0, List.of(100.0), 300.0, SourceType.ACCOUNT, BANK, 1);

        assertTrue(service.run().changed().isEmpty());
        clock.advance(Duration.ofHours(1));
        var run = service.run();

        assertEquals(OfferState.ACTIVE, run.changed().get(id));
        assertEquals(List.of(id), contestsStarted);
        assertEquals(700.0, balances.get(BANK));
        var started = offers.offer(id).orElseThrow();
        assertEquals(300.0, started.held());
        assertEquals(LocalDateTime.now(clock), started.started());
        service.run();
        assertEquals(700.0, balances.get(BANK));
    }

    @Test
    void anAccountThatCannotPayLeavesTheContestWaiting() {
        balances.put(BANK, 100.0);
        var id = contest(100, 2.0, List.of(), 200.0, SourceType.ACCOUNT, BANK, 0);

        service.run();

        assertEquals(OfferState.SCHEDULED, offers.offer(id).orElseThrow().state());
        assertEquals(100.0, balances.get(BANK));
        balances.put(BANK, 500.0);
        service.run();
        assertEquals(OfferState.ACTIVE, offers.offer(id).orElseThrow().state());
        assertEquals(300.0, balances.get(BANK));
    }

    @Test
    void anEndedContestPaysItsPrizesOnceAndGivesTheRestBack() {
        balances.put(BANK, 1000.0);
        var id = contest(100, 1.0, List.of(50.0, 30.0, 20.0), 200.0, SourceType.ACCOUNT, BANK, 0);
        service.run();
        for (var company : List.of(iron, stone, wood)) offers.join(id, company, LocalDateTime.now(clock));
        offers.deliver(id, stone, UUID.randomUUID(), 30, LocalDateTime.now(clock));
        offers.deliver(id, iron, UUID.randomUUID(), 10, LocalDateTime.now(clock));

        clock.advance(Duration.ofDays(1));
        var run = service.run();

        assertEquals(OfferState.ENDED, run.changed().get(id));
        assertEquals(List.of(id), run.settled());
        assertEquals(List.of(stone + ":50.0", iron + ":30.0"), prizesPaid);
        var settled = offers.offer(id).orElseThrow();
        assertEquals(40.0 + 80.0, settled.spent());
        assertEquals(1000.0 - 200.0 + 80.0, balances.get(BANK));
        var prizes = offers.prizes(id);
        assertEquals(3, prizes.size());
        assertNull(prizes.get(2).company());
        assertFalse(prizes.get(2).paid());
        assertEquals(List.of(id), announced);
        assertEquals(List.of(List.of(stone, iron)), rankings);
        assertEquals(List.of(List.of("1:" + stone, "2:" + iron)), paidPrizes);

        assertTrue(service.run().settled().isEmpty());
        assertEquals(2, prizesPaid.size());
        assertEquals(1, announced.size());
    }

    @Test
    void aContestFilledByItsDeliveriesIsSettledAsReached() {
        var id = contest(50, 1.0, List.of(25.0), 75.0, SourceType.MINTED, null, 0);
        service.run();
        offers.join(id, iron, LocalDateTime.now(clock));

        assertEquals(
                OfferState.REACHED,
                offers.deliver(id, iron, UUID.randomUUID(), 64, LocalDateTime.now(clock))
                        .state());
        var run = service.run();

        assertEquals(List.of(id), run.settled());
        assertEquals(List.of(iron + ":25.0"), prizesPaid);
        assertEquals(75.0, offers.offer(id).orElseThrow().spent());
    }

    @Test
    void aCancelledContestPaysNoPrizesAndGivesAllItDidNotSpendBack() {
        balances.put(BANK, 1000.0);
        var id = contest(100, 1.0, List.of(40.0), 140.0, SourceType.ACCOUNT, BANK, 0);
        service.run();
        offers.join(id, wood, LocalDateTime.now(clock));
        offers.deliver(id, wood, UUID.randomUUID(), 5, LocalDateTime.now(clock));
        offers.state(id, OfferState.ACTIVE, OfferState.CANCELLED, LocalDateTime.now(clock));

        var run = service.run();

        assertEquals(List.of(id), run.settled());
        assertTrue(prizesPaid.isEmpty());
        assertEquals(PrizeState.NONE, offers.prizes(id).getFirst().state());
        assertEquals(1000.0 - 5.0, balances.get(BANK));
        assertEquals(List.of(id), cancelled);
        assertTrue(announced.isEmpty());
    }

    @Test
    void aContestCancelledWithItsServerEventPaysNoPrizesAndGivesItsMoneyBack() {
        balances.put(BANK, 1000.0);
        var id = contest(100, 1.0, List.of(40.0), 140.0, SourceType.ACCOUNT, BANK, 0);
        var now = LocalDateTime.now(clock);
        var event = new AtomicReference<>(serverEvent(EventState.ACTIVE, now, now.plusDays(1)));
        service.useEvents(found -> found == 9 ? Optional.of(event.get()) : Optional.empty(), offer -> {});
        offers.link(id, 9);
        service.run();
        offers.join(id, wood, now);
        offers.deliver(id, wood, UUID.randomUUID(), 5, now);
        event.set(serverEvent(EventState.CANCELLED, now, now.plusDays(1)));

        service.run();
        service.run();

        assertEquals(OfferState.CANCELLED, offers.offer(id).orElseThrow().state());
        assertTrue(prizesPaid.isEmpty());
        assertEquals(1000.0 - 5.0, balances.get(BANK));
        assertEquals(List.of(id), cancelled);
    }

    private static ServerEvent serverEvent(EventState state, LocalDateTime starts, LocalDateTime ends) {
        return new ServerEvent(
                9,
                "Harvest",
                EventKind.TAX_REDUCTION,
                5,
                List.of(),
                List.of(),
                starts,
                ends,
                null,
                null,
                null,
                0,
                state,
                null,
                "Nora",
                starts,
                null,
                null);
    }

    @Test
    void aContestCancelledBeforeItStartedAnnouncesNothing() {
        var id = contest(100, 1.0, List.of(40.0), 140.0, SourceType.MINTED, null, 5);
        offers.state(id, OfferState.SCHEDULED, OfferState.CANCELLED, LocalDateTime.now(clock));

        assertEquals(List.of(id), service.run().settled());

        assertTrue(cancelled.isEmpty());
        assertTrue(announced.isEmpty());
        assertTrue(offers.working().isEmpty());
    }

    @Test
    void aSettlingStoppedBeforeItsPrizesIsFinishedByTheNextCheckPayingOnce() {
        var id = contest(100, 1.0, List.of(50.0), 150.0, SourceType.MINTED, null, 0);
        service.run();
        offers.join(id, iron, LocalDateTime.now(clock));
        offers.deliver(id, iron, UUID.randomUUID(), 10, LocalDateTime.now(clock));
        offers.state(id, OfferState.ACTIVE, OfferState.ENDED, LocalDateTime.now(clock));
        crashOnClaim = true;

        service.run();
        assertTrue(prizesPaid.isEmpty());
        assertTrue(announced.isEmpty());
        assertEquals(PrizeState.PENDING, offers.prizes(id).getFirst().state());

        service.run();
        service.run();
        assertEquals(List.of(iron + ":50.0"), prizesPaid);
        assertEquals(List.of(id), announced);
        assertEquals(List.of(List.of("1:" + iron)), paidPrizes);
    }

    @Test
    void theRestIsKeptWhileAPrizeStillWaits() {
        balances.put(BANK, 1000.0);
        var id = contest(100, 1.0, List.of(50.0), 150.0, SourceType.ACCOUNT, BANK, 0);
        service.run();
        offers.join(id, iron, LocalDateTime.now(clock));
        offers.deliver(id, iron, UUID.randomUUID(), 10, LocalDateTime.now(clock));
        offers.state(id, OfferState.ACTIVE, OfferState.ENDED, LocalDateTime.now(clock));
        refuseClaim = true;

        service.run();
        assertTrue(prizesPaid.isEmpty());
        assertNull(offers.offer(id).orElseThrow().refunded());
        assertEquals(850.0, balances.get(BANK));
        assertTrue(announced.isEmpty());

        service.run();
        assertEquals(List.of(iron + ":50.0"), prizesPaid);
        assertEquals(1000.0 - 150.0 + 90.0, balances.get(BANK));
        assertEquals(List.of(id), announced);
    }

    @Test
    void aPrizeWhoseOutcomeCannotBeWrittenIsNotPaidAgain() {
        balances.put(BANK, 1000.0);
        var id = contest(100, 1.0, List.of(50.0), 150.0, SourceType.ACCOUNT, BANK, 0);
        service.run();
        offers.join(id, iron, LocalDateTime.now(clock));
        offers.deliver(id, iron, UUID.randomUUID(), 10, LocalDateTime.now(clock));
        offers.state(id, OfferState.ACTIVE, OfferState.ENDED, LocalDateTime.now(clock));
        failOutcome = true;

        service.run();
        failOutcome = false;
        service.run();

        assertEquals(List.of(iron + ":50.0"), prizesPaid);
        assertEquals(PrizeState.PAYING, offers.prizes(id).getFirst().state());
        assertEquals(1000.0 - 150.0 + 90.0, balances.get(BANK));
        assertTrue(offers.working().isEmpty());
    }

    @Test
    void aDeletedCompanyGetsNoPlace() {
        var id = contest(100, 1.0, List.of(50.0, 25.0), 175.0, SourceType.MINTED, null, 0);
        service.run();
        offers.join(id, iron, LocalDateTime.now(clock));
        offers.join(id, stone, LocalDateTime.now(clock));
        offers.deliver(id, iron, UUID.randomUUID(), 30, LocalDateTime.now(clock));
        offers.deliver(id, stone, UUID.randomUUID(), 10, LocalDateTime.now(clock));
        query("DELETE FROM companies WHERE id = ?").single(call().bind(iron)).delete();
        offers.state(id, OfferState.ACTIVE, OfferState.ENDED, LocalDateTime.now(clock));

        service.run();

        assertEquals(List.of(stone + ":50.0"), prizesPaid);
        assertEquals(List.of(List.of(stone)), rankings);
    }

    @Test
    void aPrizeThatCannotBePaidStaysWithTheAccount() {
        balances.put(BANK, 1000.0);
        failingWinner = iron;
        var id = contest(100, 1.0, List.of(60.0), 160.0, SourceType.ACCOUNT, BANK, 0);
        service.run();
        offers.join(id, iron, LocalDateTime.now(clock));
        offers.deliver(id, iron, UUID.randomUUID(), 10, LocalDateTime.now(clock));
        offers.state(id, OfferState.ACTIVE, OfferState.ENDED, LocalDateTime.now(clock));

        service.run();

        assertTrue(prizesPaid.isEmpty());
        assertEquals(PrizeState.FAILED, offers.prizes(id).getFirst().state());
        assertEquals(1000.0 - 10.0, balances.get(BANK));
    }

    @Test
    void aContestWhoseMoneyCannotPayAnItemIsDepleted() {
        var id = contest(100, 3.0, List.of(10.0), 12.0, SourceType.MINTED, null, 0);

        var run = service.run();

        assertEquals(OfferState.DEPLETED, run.changed().get(id));
        assertEquals(List.of(id), run.settled());
    }

    @Test
    void aPausedContestThatNeverStartedTakesItsMoneyWhenResumed() {
        balances.put(BANK, 1000.0);
        var id = contest(100, 1.0, List.of(), 100.0, SourceType.ACCOUNT, BANK, 2);
        offers.state(id, OfferState.SCHEDULED, OfferState.PAUSED, LocalDateTime.now(clock));
        clock.advance(Duration.ofHours(3));
        service.run();
        assertEquals(1000.0, balances.get(BANK));

        offers.state(id, OfferState.PAUSED, OfferState.SCHEDULED, LocalDateTime.now(clock));
        service.run();

        assertEquals(OfferState.ACTIVE, offers.offer(id).orElseThrow().state());
        assertEquals(900.0, balances.get(BANK));
        offers.state(id, OfferState.ACTIVE, OfferState.PAUSED, LocalDateTime.now(clock));
        offers.state(id, OfferState.PAUSED, OfferState.SCHEDULED, LocalDateTime.now(clock));
        service.run();
        assertEquals(List.of(id), contestsStarted);
    }

    private static int company(String name) {
        query("INSERT INTO companies(name) VALUES (?)")
                .single(call().bind(name))
                .insert();
        return query("SELECT id FROM companies WHERE name = ?")
                .single(call().bind(name))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    private int contest(
            int amount,
            double unit,
            List<Double> prizes,
            double budget,
            SourceType source,
            @Nullable String account,
            int startHours) {
        var now = LocalDateTime.now(clock);
        return offers.create(EventOffer.contest(
                        "Race",
                        OfferLine.material("OAK_LOG"),
                        amount,
                        unit,
                        prizes,
                        source,
                        account,
                        budget,
                        now.plusHours(startHours),
                        now.plusHours(startHours).plusDays(1),
                        "Admin",
                        now))
                .orElseThrow();
    }

    private final class FakeFunding implements SystemOrderService.Funding {
        @Override
        public boolean withdraw(String account, double amount) {
            var balance = balances.getOrDefault(account, 0.0);
            if (balance < amount) return false;
            balances.put(account, balance - amount);
            return true;
        }

        @Override
        public void deposit(String account, double amount) {
            balances.merge(account, amount, Double::sum);
        }
    }

    private static final class NoPlacement implements SystemOrderService.Placement {
        @Override
        public boolean knows(String material) {
            return true;
        }

        @Override
        public OptionalDouble average(String material) {
            return OptionalDouble.empty();
        }

        @Override
        public String canonical(String material) {
            return material;
        }

        @Override
        public int stackSize(String material) {
            return 64;
        }

        @Override
        public CompletableFuture<SystemOrderService.Placed> place(SystemOrderService.Wanted wanted) {
            throw new IllegalStateException("contests place no orders");
        }

        @Override
        public boolean remove(int order) {
            throw new IllegalStateException("contests remove no orders");
        }
    }
}
