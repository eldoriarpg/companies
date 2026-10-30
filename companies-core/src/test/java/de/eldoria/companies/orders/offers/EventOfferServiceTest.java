/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.offers;

import de.eldoria.companies.components.event.EventKind;
import de.eldoria.companies.components.event.EventState;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.dashboard.MutableClock;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOfferData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.GoalReward;
import de.eldoria.companies.data.wrapper.offer.OfferLine;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.data.wrapper.offer.OpenChunk;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.companies.orders.OrderTable;
import de.eldoria.companies.orders.SystemOwner;
import de.eldoria.companies.orders.system.SystemOrderService;
import de.eldoria.companies.util.Times;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import tools.jackson.databind.json.JsonMapper;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventOfferServiceTest {
    private static final String BANK = "Harbour fund";
    private static final int COMPANY = 7;

    @TempDir
    Path directory;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-29T08:00:00Z"));
    private final Map<String, Double> balances = new HashMap<>();
    private final List<SystemOrderService.Wanted> wanted = new ArrayList<>();
    private final List<Integer> removed = new ArrayList<>();
    private final List<EventOffer> announced = new ArrayList<>();
    private final List<Integer> started = new ArrayList<>();
    private final List<String> progressed = new ArrayList<>();
    private Logger logger;
    private SqLiteOrderData orderData;
    private OrderTable system;
    private SqLiteOfferData offers;
    private EventOfferService service;
    private @Nullable Double changedPrice;
    private boolean failStore;
    private boolean failRemove;
    private boolean refuseRemove;
    private int openChunkReads;
    private @Nullable Runnable onSecondRead;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        Times.useZone(() -> ZoneOffset.UTC);
        SqLiteTestDatabase.create(directory);
        var orderData = new SqLiteOrderData(null, SqLiteTestDatabase.mapper());
        system = new OrderTable(orderData, SqLiteTestDatabase.player(SystemOwner.ID));
        offers = new SqLiteOfferData(SqLiteTestDatabase.mapper()) {
            @Override
            public boolean addChunk(
                    int offer, int order, int amount, double paid, @Nullable String account, LocalDateTime at) {
                return !failStore && super.addChunk(offer, order, amount, paid, account, at);
            }

            @Override
            public List<OpenChunk> openChunks(int offer) {
                var open = super.openChunks(offer);
                if (++openChunkReads == 2 && onSecondRead != null) onSecondRead.run();
                return open;
            }
        };
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        this.logger = logger;
        this.orderData = orderData;
        service = service();
    }

    private EventOfferService service() {
        return new EventOfferService(
                offers,
                orderData,
                new FakePlacement(),
                new FakeFunding(),
                new EventOfferService.Announcer() {
                    @Override
                    public void reached(EventOffer offer) {
                        announced.add(offer);
                    }

                    @Override
                    public void started(EventOffer offer) {
                        started.add(offer.id());
                    }

                    @Override
                    public void goalProgress(EventOffer offer, int threshold) {
                        progressed.add(offer.id() + ":" + threshold);
                    }
                },
                clock,
                () -> 0.5,
                logger);
    }

    @Test
    void aGoalAnnouncesItsStartOnceAndNotWhenResumed() {
        var id = goal(500, 2.0, 100, 1, SourceType.MINTED, null, 1000.0, 1, 24);

        service.run();
        assertTrue(started.isEmpty());
        clock.advance(Duration.ofHours(1));
        service.run();
        offers.state(id, OfferState.ACTIVE, OfferState.PAUSED, LocalDateTime.now(clock));
        service.run();
        offers.state(id, OfferState.PAUSED, OfferState.ACTIVE, LocalDateTime.now(clock));
        service.run();

        assertEquals(List.of(id), started);
    }

    @Test
    void eachProgressThresholdIsAnnouncedOnceAlsoAfterARestart() {
        var id = goal(400, 1.0, 100, 4, SourceType.MINTED, null, 400.0);
        var placed = service.run().placed();
        claim(placed.get(0));
        deliver(placed.get(0), 100);

        service.run();
        service.run();
        assertEquals(List.of(id + ":25"), progressed);

        service = service();
        service.run();
        assertEquals(List.of(id + ":25"), progressed);

        for (var order : placed.subList(1, 3)) {
            claim(order);
            deliver(order, 100);
        }
        service.run();
        assertEquals(List.of(id + ":25", id + ":75"), progressed);

        claim(placed.get(3));
        deliver(placed.get(3), 100);
        service.run();
        assertEquals(List.of(id + ":25", id + ":75", id + ":100"), progressed);
        assertEquals(1, announced.size());
    }

    @Test
    void aGoalPausedBeforeItsStartAndResumedAfterItIsStartedAndAnnounced() {
        var id = goal(500, 2.0, 100, 1, SourceType.MINTED, null, 1000.0, 1, 24);
        offers.state(id, OfferState.SCHEDULED, OfferState.PAUSED, LocalDateTime.now(clock));
        clock.advance(Duration.ofHours(2));
        service.run();
        assertTrue(started.isEmpty());

        offers.state(id, OfferState.PAUSED, OfferState.SCHEDULED, LocalDateTime.now(clock));
        var run = service.run();

        assertEquals(OfferState.ACTIVE, run.changed().get(id));
        assertEquals(List.of(id), started);
        assertEquals(LocalDateTime.now(clock), offer(id).started());
        assertEquals(1, run.placed().size());
    }

    @Test
    void anEndedGoalPostsNoMoreProgress() {
        var id = goal(400, 1.0, 100, 1, SourceType.MINTED, null, 400.0, 0, 24);
        var first = service.run().placed().getFirst();
        claim(first);
        clock.advance(Duration.ofHours(24));
        service.run();
        assertEquals(OfferState.ENDED, offer(id).state());

        deliver(first, 100);
        service.run();

        assertEquals(100, offer(id).delivered());
        assertTrue(progressed.isEmpty());
    }

    @Test
    void thresholdsArePassedAtTheirPercent() {
        assertEquals(0, EventOfferService.threshold(24));
        assertEquals(25, EventOfferService.threshold(25));
        assertEquals(50, EventOfferService.threshold(74));
        assertEquals(100, EventOfferService.threshold(100));
    }

    @AfterEach
    void tearDown() {
        Times.useZone(Times::machineZone);
        MockBukkit.unmock();
    }

    @Test
    void aStartedGoalKeepsItsChunksOpenTheLastOneTheRest() {
        var id = goal(500, 2.0, 200, 3, SourceType.MINTED, null, 1000.0);

        var run = service.run();

        assertEquals(OfferState.ACTIVE, run.changed().get(id));
        assertEquals(List.of(200, 200, 100), amounts());
        assertTrue(wanted.stream().allMatch(order -> order.rule().equals(EventOffer.RULE)));
        assertEquals("Harbour logs", wanted.getFirst().name());
        assertEquals("OAK_LOG", wanted.getFirst().material());
        var offer = offer(id);
        assertEquals(1000.0, offer.spent());
        assertEquals(3, offers.openChunks(id).size());
        for (var order : run.placed()) assertEquals(id, offerOf(order));
        assertTrue(service.run().placed().isEmpty());
    }

    @Test
    void aScheduledGoalWaitsForItsStart() {
        var id = goal(500, 2.0, 100, 2, SourceType.MINTED, null, 1000.0, 2, 24);

        assertTrue(service.run().placed().isEmpty());
        assertEquals(OfferState.SCHEDULED, offer(id).state());
        clock.advance(Duration.ofHours(2));
        assertEquals(2, service.run().placed().size());
        assertEquals(OfferState.ACTIVE, offer(id).state());
    }

    @Test
    void aTakenChunkIsReplacedByTheNext() {
        var id = goal(500, 2.0, 100, 2, SourceType.MINTED, null, 1000.0);
        var first = service.run().placed().getFirst();

        claim(first);
        var run = service.run();

        assertEquals(1, run.placed().size());
        assertEquals(3, offers.openChunks(id).size());
        assertEquals(List.of(100, 100, 100), amounts());
    }

    @Test
    void deliveredChunksCountForTheGoalAndTheirCompany() {
        var id = goal(300, 2.0, 100, 1, SourceType.MINTED, null, 600.0);
        var first = service.run().placed().getFirst();
        claim(first);
        deliver(first, 100);

        var run = service.run();

        assertEquals(List.of(first), run.delivered());
        var offer = offer(id);
        assertEquals(100, offer.delivered());
        assertEquals(33, offer.percent());
        var contributors = offers.contributors(id, 5);
        assertEquals(1, contributors.size());
        assertEquals(COMPANY, contributors.getFirst().company());
        assertEquals(100, contributors.getFirst().delivered());
        assertEquals(1, run.placed().size());
    }

    @Test
    void anOfferOfAServerEventFollowsItsTimesAndEndsAndIsCancelledWithIt() {
        var id = goal(500, 2.0, 100, 1, SourceType.MINTED, null, 1000.0, 2, 24);
        var now = LocalDateTime.now(clock);
        var event = new AtomicReference<>(serverEvent(EventState.SCHEDULED, now.plusHours(1), now.plusHours(5)));
        offers.link(id, 9);
        service.useEvents(found -> found == 9 ? Optional.of(event.get()) : Optional.empty(), offer -> {});

        service.run();
        var followed = offers.offer(id).orElseThrow();
        assertEquals(now.plusHours(1).withNano(0), followed.starts().withNano(0));
        assertEquals(now.plusHours(5).withNano(0), followed.ends().withNano(0));

        event.set(serverEvent(EventState.ENDED, now.plusHours(1), now.plusHours(5)));
        clock.advance(Duration.ofHours(2));
        service.run();
        service.run();
        assertEquals(OfferState.ENDED, offers.offer(id).orElseThrow().state());

        var other = goal(500, 2.0, 100, 1, SourceType.MINTED, null, 1000.0, 0, 24);
        offers.link(other, 9);
        event.set(serverEvent(EventState.CANCELLED, now, now.plusHours(5)));
        service.run();
        assertEquals(OfferState.CANCELLED, offers.offer(other).orElseThrow().state());
    }

    @Test
    void aScheduledOfferWhoseEventEndedEndsWithoutAStartAnnouncement() {
        var id = goal(500, 2.0, 100, 1, SourceType.MINTED, null, 1000.0, 2, 24);
        var now = LocalDateTime.now(clock);
        offers.link(id, 9);
        service.useEvents(
                found -> Optional.of(serverEvent(EventState.ENDED, now.plusHours(2), now.plusHours(5))), offer -> {});

        service.run();

        assertEquals(OfferState.ENDED, offers.offer(id).orElseThrow().state());
        assertTrue(started.isEmpty());
    }

    @Test
    void aRewardThatFailedIsCreatedOnALaterCheckOnce() {
        var calls = new ArrayList<Integer>();
        service.useEvents(found -> Optional.empty(), offer -> {
            calls.add(offer.id());
            if (calls.size() == 1) throw new IllegalStateException("The server stops");
            offers.rewarded(offer.id(), 77);
        });
        var id = goal(200, 2.0, 100, 2, SourceType.MINTED, null, 400.0);
        offers.reward(id, new GoalReward(1.5, 60, null, null, null));
        for (var order : service.run().placed()) {
            claim(order);
            deliver(order, 100);
        }

        service.run();
        service.run();
        service.run();

        assertEquals(List.of(id, id), calls);
        assertEquals(Optional.of(77), offers.rewardEvent(id));
    }

    @Test
    void aReachedGoalHandsItsRewardOverOnce() {
        var rewarded = new ArrayList<Integer>();
        service.useEvents(found -> Optional.empty(), offer -> rewarded.add(offer.id()));
        var id = goal(200, 2.0, 100, 2, SourceType.MINTED, null, 400.0);
        for (var order : service.run().placed()) {
            claim(order);
            deliver(order, 100);
        }

        service.run();
        service.run();

        assertEquals(List.of(id), rewarded);
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
    void aReachedGoalIsAnnouncedOnce() {
        var id = goal(200, 2.0, 100, 2, SourceType.MINTED, null, 400.0);
        var placed = service.run().placed();
        for (var order : placed) {
            claim(order);
            deliver(order, 100);
        }

        var run = service.run();

        assertEquals(OfferState.REACHED, run.changed().get(id));
        assertEquals(1, announced.size());
        assertEquals(id, announced.getFirst().id());
        assertEquals(200, announced.getFirst().delivered());
        assertTrue(run.placed().isEmpty());
        service.run();
        assertEquals(1, announced.size());
    }

    @Test
    void anEndedGoalWithdrawsItsWaitingChunksAndStillCountsTakenOnes() {
        balances.put(BANK, 1000.0);
        var id = goal(500, 2.0, 100, 2, SourceType.ACCOUNT, BANK, 1000.0, 0, 24);
        var placed = service.run().placed();
        assertEquals(600.0, balances.get(BANK));
        claim(placed.getFirst());

        clock.advance(Duration.ofHours(24));
        var run = service.run();

        assertEquals(OfferState.ENDED, run.changed().get(id));
        assertEquals(List.of(placed.get(1)), run.withdrawn());
        assertEquals(List.of(placed.get(1)), removed);
        assertEquals(800.0, balances.get(BANK));
        assertEquals(200.0, offer(id).spent());
        assertTrue(run.placed().isEmpty());

        deliver(placed.getFirst(), 100);
        service.run();
        assertEquals(100, offer(id).delivered());
        assertTrue(offers.working().isEmpty());
    }

    @Test
    void aCancelledGoalWithdrawsItsWaitingChunks() {
        var id = goal(500, 2.0, 100, 2, SourceType.MINTED, null, 1000.0);
        var placed = service.run().placed();

        assertTrue(offers.state(id, OfferState.ACTIVE, OfferState.CANCELLED, LocalDateTime.now(clock)));
        var run = service.run();

        assertEquals(placed, run.withdrawn());
        assertEquals(0.0, offer(id).spent());
        assertTrue(offers.openChunks(id).isEmpty());
        assertTrue(offers.working().isEmpty());
    }

    @Test
    void aPausedGoalWithdrawsAndAResumedOnePlacesAgain() {
        var id = goal(500, 2.0, 100, 2, SourceType.MINTED, null, 1000.0);
        service.run();

        offers.state(id, OfferState.ACTIVE, OfferState.PAUSED, LocalDateTime.now(clock));
        assertEquals(2, service.run().withdrawn().size());
        assertTrue(service.run().placed().isEmpty());

        offers.state(id, OfferState.PAUSED, OfferState.ACTIVE, LocalDateTime.now(clock));
        assertEquals(2, service.run().placed().size());
    }

    @Test
    void anAbortedChunkWaitsAgainAndARemovedOneIsPlacedAgain() {
        var id = goal(300, 2.0, 100, 1, SourceType.MINTED, null, 600.0);
        var first = service.run().placed().getFirst();
        claim(first);
        var second = service.run().placed().getFirst();

        SqLiteTestDatabase.setState(first, OrderState.UNCLAIMED.stateId(), 0);
        assertTrue(service.run().placed().isEmpty());
        assertEquals(2, offers.openChunks(id).size());

        delete(first);
        claim(second);
        var run = service.run();

        assertEquals(List.of(first), run.returned());
        assertEquals(1, run.placed().size());
        assertEquals(100, wanted.getLast().amount());
        assertEquals(400.0, offer(id).spent());
    }

    @Test
    void aGoalWhoseBudgetCannotPayTheRestIsDepleted() {
        var id = goal(300, 2.0, 100, 2, SourceType.MINTED, null, 250.0);
        var placed = service.run().placed();

        assertEquals(List.of(100, 25), amounts());
        assertEquals(OfferState.ACTIVE, offer(id).state());
        for (var order : placed) {
            claim(order);
            deliver(order, order.equals(placed.getFirst()) ? 100 : 25);
        }

        var run = service.run();

        assertEquals(OfferState.DEPLETED, run.changed().get(id));
        assertEquals(125, offer(id).delivered());
        assertTrue(announced.isEmpty());
    }

    @Test
    void aShortDeliveryKeepsOnlyTheMoneyOfWhatWasDelivered() {
        var id = goal(100, 2.0, 100, 1, SourceType.MINTED, null, 200.0);
        var first = service.run().placed().getFirst();
        claim(first);
        deliver(first, 40);

        var run = service.run();

        assertEquals(40, offer(id).delivered());
        assertEquals(80.0 + 120.0, offer(id).spent(), 0.001);
        assertEquals(60, wanted.getLast().amount());
        assertEquals(1, run.placed().size());
    }

    @Test
    void anAccountThatCannotPayPlacesNothing() {
        balances.put(BANK, 50.0);
        var id = goal(500, 2.0, 100, 2, SourceType.ACCOUNT, BANK, 1000.0);

        var run = service.run();

        assertTrue(run.placed().isEmpty());
        assertEquals(OfferState.ACTIVE, offer(id).state());
        assertEquals(50.0, balances.get(BANK));
    }

    @Test
    void aRaisedPriceIsPaidByTheAccountAndCounted() {
        balances.put(BANK, 1000.0);
        changedPrice = 250.0;
        var id = goal(100, 2.0, 100, 1, SourceType.ACCOUNT, BANK, 1000.0);

        service.run();

        assertEquals(750.0, balances.get(BANK));
        assertEquals(250.0, offer(id).spent());
        var decision = system.orderData().systemDecision(placedOrder()).orElseThrow();
        assertEquals(OfferRecord.KIND, decision.kind());
        assertEquals(EventOffer.RULE, decision.rule());
        var record = JsonMapper.builder().build().readValue(decision.record(), OfferRecord.class);
        assertEquals(id, record.offer());
        assertEquals(100, record.amount());
    }

    @Test
    void aChunkThatCannotBeNotedIsRemovedWithItsMoney() {
        balances.put(BANK, 1000.0);
        failStore = true;
        var id = goal(100, 2.0, 100, 1, SourceType.ACCOUNT, BANK, 1000.0);

        var run = service.run();

        assertTrue(run.placed().isEmpty());
        assertEquals(1, removed.size());
        assertEquals(1000.0, balances.get(BANK));
        assertEquals(0.0, offer(id).spent());
    }

    @Test
    void theChunksDoNotCountForTheMintedCapOfTheRules() {
        goal(100, 2.0, 100, 1, SourceType.MINTED, null, 200.0);

        service.run();

        assertEquals(
                0.0, system.orderData().mintedSpent(LocalDateTime.now(clock).minusDays(1)));
    }

    @Test
    void aPriceRaisedBeyondTheBudgetDepletesAGoalWithNothingOpen() {
        var id = goal(200, 2.0, 100, 1, SourceType.MINTED, null, GoalDraft.budget(2.0, 200, 100));
        changedPrice = 500.0;

        var run = service.run();

        assertEquals(OfferState.DEPLETED, run.changed().get(id));
        assertTrue(run.placed().isEmpty());
        assertEquals(1, removed.size());
        assertEquals(0.0, offer(id).spent());
        assertTrue(service.run().placed().isEmpty());
        assertEquals(1, removed.size());
    }

    @Test
    void aPriceRaisedBeyondTheBudgetWaitsForTheOpenChunks() {
        var id = goal(300, 2.0, 100, 2, SourceType.MINTED, null, GoalDraft.budget(2.0, 300, 100));
        changedPrice = 350.0;

        var first = service.run();

        assertEquals(1, first.placed().size());
        assertEquals(1, removed.size());
        assertEquals(OfferState.ACTIVE, offer(id).state());
        assertTrue(service.run().placed().isEmpty());
        assertEquals(1, removed.size());

        var chunk = first.placed().getFirst();
        claim(chunk);
        deliver(chunk, 100);
        changedPrice = null;
        var settled = service.run();
        assertEquals(List.of(chunk), settled.delivered());
        assertEquals(2, settled.placed().size());
    }

    @Test
    void aPausedGoalDeliveredInFullIsReached() {
        var id = goal(200, 2.0, 100, 2, SourceType.MINTED, null, 400.0);
        var placed = service.run().placed();
        for (var order : placed) claim(order);
        offers.state(id, OfferState.ACTIVE, OfferState.PAUSED, LocalDateTime.now(clock));
        for (var order : placed) deliver(order, 100);

        var run = service.run();

        assertEquals(OfferState.REACHED, run.changed().get(id));
        assertEquals(1, announced.size());
    }

    @Test
    void aChunkThatCannotBeWithdrawnKeepsItsMoneyUntilTheNextCheck() {
        balances.put(BANK, 1000.0);
        var id = goal(500, 2.0, 100, 1, SourceType.ACCOUNT, BANK, 1000.0);
        var chunk = service.run().placed().getFirst();
        offers.state(id, OfferState.ACTIVE, OfferState.PAUSED, LocalDateTime.now(clock));

        failRemove = true;
        assertTrue(service.run().withdrawn().isEmpty());
        failRemove = false;
        refuseRemove = true;
        assertTrue(service.run().withdrawn().isEmpty());
        assertEquals(800.0, balances.get(BANK));
        assertEquals(200.0, offer(id).spent());
        assertEquals(1, offers.openChunks(id).size());

        refuseRemove = false;
        assertEquals(List.of(chunk), service.run().withdrawn());
        assertEquals(1000.0, balances.get(BANK));
        assertEquals(0.0, offer(id).spent());
    }

    @Test
    void aChunkSettledWhilePlacingIsNeverCountedAsMissing() {
        var id = goal(300, 2.0, 100, 3, SourceType.MINTED, null, 600.0);
        var placed = service.run().placed();
        var first = placed.getFirst();
        claim(first);
        openChunkReads = 0;
        onSecondRead = () -> {
            deliver(first, 100);
            offers.chunkDelivered(first, LocalDateTime.now(clock));
        };

        var run = service.run();

        assertTrue(run.placed().isEmpty());
        assertEquals(100, offer(id).delivered());
        assertEquals(List.of(100, 100, 100), amounts());
    }

    @Test
    void affordableItemsStayWithinTheBudgetAtTheRoundedPrice() {
        assertEquals(100, EventOffer.affordable(2.0, 100, 1000));
        assertEquals(33, EventOffer.affordable(3.0, 100, 100));
        assertEquals(0, EventOffer.affordable(3.0, 100, 2.99));
        assertEquals(2, EventOffer.affordable(0.335, 10, 0.67));
        assertEquals(0, EventOffer.affordable(2.0, 0, 1000));
    }

    private int goal(
            int amount, double unit, int chunk, int open, SourceType source, @Nullable String account, double budget) {
        return goal(amount, unit, chunk, open, source, account, budget, 0, 72);
    }

    private int goal(
            int amount,
            double unit,
            int chunk,
            int open,
            SourceType source,
            @Nullable String account,
            double budget,
            int startHours,
            int hours) {
        var now = LocalDateTime.now(clock);
        var starts = now.plusHours(startHours);
        return offers.create(EventOffer.goal(
                        "Harbour logs",
                        OfferLine.material("OAK_LOG"),
                        amount,
                        unit,
                        chunk,
                        open,
                        source,
                        account,
                        budget,
                        starts,
                        starts.plusHours(hours),
                        "Admin",
                        now))
                .orElseThrow();
    }

    private EventOffer offer(int id) {
        return offers.offer(id).orElseThrow();
    }

    private List<Integer> amounts() {
        return wanted.stream().map(SystemOrderService.Wanted::amount).toList();
    }

    private int placedOrder() {
        return query("SELECT MAX(order_id) AS id FROM event_offer_chunks")
                .single()
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    private static int offerOf(int order) {
        return query("SELECT offer_id FROM orders WHERE id = ?")
                .single(call().bind(order))
                .map(row -> row.getInt("offer_id"))
                .first()
                .orElseThrow();
    }

    private static void claim(int order) {
        SqLiteTestDatabase.setState(order, OrderState.CLAIMED.stateId(), COMPANY);
    }

    private void deliver(int order, int amount) {
        var simple = system.orderData().retrieveOrderById(order).join().orElseThrow();
        system.deliver(simple, 0, Material.OAK_LOG, UUID.randomUUID(), amount);
        SqLiteTestDatabase.setState(order, OrderState.RECEIVED.stateId(), COMPANY);
    }

    private static void delete(int order) {
        for (var table : List.of("order_content", "order_states", "orders")) {
            query("DELETE FROM " + table + " WHERE id = ?")
                    .single(call().bind(order))
                    .delete();
        }
    }

    private final class FakePlacement implements SystemOrderService.Placement {
        @Override
        public boolean knows(String material) {
            return !material.equals("NOT_AN_ITEM");
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
        public CompletableFuture<SystemOrderService.Placed> place(SystemOrderService.Wanted order) {
            wanted.add(order);
            var placed = system.place(0.0);
            system.orderData().systemRule(placed.id(), order.rule());
            var price = changedPrice != null ? changedPrice : OrderBuilder.total(order.unit(), order.amount());
            return CompletableFuture.completedFuture(new SystemOrderService.Created(placed.id(), price));
        }

        @Override
        public boolean remove(int order) {
            if (failRemove) throw new IllegalStateException("removing failed");
            if (refuseRemove) return false;
            var found = system.orderData().retrieveOrderById(order).join();
            if (found.isEmpty() || found.get().state() != OrderState.UNCLAIMED) return false;
            removed.add(order);
            delete(order);
            return true;
        }
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
}
