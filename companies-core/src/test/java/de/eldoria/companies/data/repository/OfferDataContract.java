/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.configuration.elements.systemorders.RuleEntry;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.offer.ContestPrize;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferKind;
import de.eldoria.companies.data.wrapper.offer.OfferLine;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.data.wrapper.offer.PrizeState;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.data.wrapper.order.SystemDecision;
import de.eldoria.companies.orders.SystemOwner;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What every dialect does with the tables and queries of event offers. The database is started once per dialect and
 * emptied before each test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class OfferDataContract {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 12, 0);

    private DataSource dataSource;
    private AOrderData orders;
    private AOfferData offers;
    private OfflinePlayer server;

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
     * @return the dialect's order tables
     */
    protected abstract AOrderData orders();

    /**
     * @return the dialect's offer tables
     */
    protected abstract AOfferData offers();

    @BeforeAll
    void startDatabase() throws Exception {
        MockBukkit.mock();
        dataSource = start();
    }

    @BeforeEach
    void setUp() {
        QueryConfiguration.setDefault(QueryConfiguration.builder(dataSource).build());
        for (var table : List.of(
                "event_offer_chunks",
                "contest_deliveries",
                "contest_prizes",
                "contest_entries",
                "event_offers",
                "order_deliveries",
                "order_content",
                "order_states",
                "orders",
                "system_order_decisions",
                "companies")) {
            query("DELETE FROM " + table).single().delete();
        }
        orders = orders();
        offers = offers();
        server = SqLiteTestDatabase.player(SystemOwner.ID);
    }

    @AfterAll
    void stopDatabase() {
        try {
            if (dataSource != null) stop();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void offersAreStoredAndReadWithTheirLines() {
        var material = offers.create(goal("Harbour logs", OfferLine.material("OAK_LOG"), SourceType.MINTED, null))
                .orElseThrow();
        var criteria = offers.create(goal(
                        "Any logs", OfferLine.criteria("#logs", RuleEntry.Resolve.ANY), SourceType.ACCOUNT, "Bank"))
                .orElseThrow();
        var item = offers.create(goal(
                        "Swords",
                        OfferLine.item(new ItemStack(Material.DIAMOND_SWORD), "Diamond Sword"),
                        SourceType.MINTED,
                        null))
                .orElseThrow();

        var read = offers.offer(material).orElseThrow();
        assertEquals(OfferKind.GOAL, read.kind());
        assertEquals("Harbour logs", read.name());
        assertNull(read.event());
        assertEquals("OAK_LOG", read.line().material());
        assertEquals("OAK_LOG", read.line().label());
        assertEquals(1000, read.amount());
        assertEquals(2.5, read.unitPrice());
        assertEquals(100, read.chunkSize());
        assertEquals(3, read.openChunks());
        assertEquals(SourceType.MINTED, read.source());
        assertNull(read.account());
        assertEquals(2500.0, read.budget());
        assertEquals(0.0, read.spent());
        assertEquals(0, read.delivered());
        assertEquals(NOW, read.starts());
        assertEquals(NOW.plusDays(3), read.ends());
        assertEquals(OfferState.SCHEDULED, read.state());
        assertEquals("Admin", read.createdBy());
        assertEquals(NOW.minusHours(1), read.created());
        assertNull(read.closed());

        var withCriteria = offers.offer(criteria).orElseThrow();
        assertEquals("#logs", withCriteria.line().criteria());
        assertEquals(RuleEntry.Resolve.ANY, withCriteria.line().resolve());
        assertEquals("Bank", withCriteria.account());
        assertEquals(SourceType.ACCOUNT, withCriteria.source());

        var withItem = offers.offer(item).orElseThrow();
        assertNotNull(withItem.line().item());
        assertEquals(Material.DIAMOND_SWORD, withItem.line().item().getType());
        assertEquals("Diamond Sword", withItem.line().label());

        assertEquals(
                List.of(item, criteria, material),
                offers.offers(10).stream().map(EventOffer::id).toList());
        assertEquals(
                List.of(item, criteria),
                offers.offers(2).stream().map(EventOffer::id).toList());
        assertTrue(offers.offer(item + 100).isEmpty());
    }

    @Test
    void statesChangeOnceAndAFinalStateNotesTheClose() {
        var id = offers.create(goal("Logs", OfferLine.material("OAK_LOG"), SourceType.MINTED, null))
                .orElseThrow();

        assertTrue(offers.state(id, OfferState.SCHEDULED, OfferState.ACTIVE, NOW));
        assertFalse(offers.state(id, OfferState.SCHEDULED, OfferState.ACTIVE, NOW));
        assertNull(offers.offer(id).orElseThrow().closed());
        assertTrue(offers.state(id, OfferState.ACTIVE, OfferState.CANCELLED, NOW.plusHours(1)));
        var closed = offers.offer(id).orElseThrow();
        assertEquals(OfferState.CANCELLED, closed.state());
        assertEquals(NOW.plusHours(1), closed.closed());
        assertTrue(offers.chunkLimit(id, 5));
        assertEquals(5, offers.offer(id).orElseThrow().openChunks());
        assertFalse(offers.chunkLimit(id + 100, 5));
    }

    @Test
    void chunksMarkTheirOrdersAndHoldTheOffersMoney() {
        var id = active();
        var chunk = order();
        var other = order();

        assertTrue(offers.addChunk(id, chunk.id(), 100, 250.0, "Bank", NOW));

        assertEquals(250.0, offers.offer(id).orElseThrow().spent());
        var open = offers.openChunks(id);
        assertEquals(1, open.size());
        assertEquals(chunk.id(), open.getFirst().order());
        assertEquals(100, open.getFirst().amount());
        assertEquals("Bank", open.getFirst().account());
        assertEquals(OrderState.UNCLAIMED, open.getFirst().state());
        assertTrue(open.getFirst().unclaimed());

        var marks = offers.marks(List.of(chunk.id(), other.id()));
        assertEquals(1, marks.size());
        var mark = marks.get(chunk.id());
        assertEquals(id, mark.offer());
        assertEquals("Logs", mark.name());
        assertEquals(NOW.plusDays(3), mark.ends());
        assertEquals(OfferState.ACTIVE, mark.state());
        assertTrue(offers.marks(List.of()).isEmpty());
    }

    @Test
    void aDeliveredChunkCountsOnceForItsOfferAndItsCompany() {
        var id = active();
        var chunk = order();
        offers.addChunk(id, chunk.id(), 100, 250.0, null, NOW);
        var company = company("Iron Works");
        claim(chunk, company);
        deliver(chunk, 60);
        deliver(chunk, 40);

        assertFalse(offers.chunkDelivered(chunk.id(), NOW));
        state(chunk, OrderState.RECEIVED);
        assertTrue(offers.chunkDelivered(chunk.id(), NOW));
        assertFalse(offers.chunkDelivered(chunk.id(), NOW));

        var offer = offers.offer(id).orElseThrow();
        assertEquals(100, offer.delivered());
        assertEquals(250.0, offer.spent(), 0.001);
        assertTrue(offers.openChunks(id).isEmpty());
        var contributors = offers.contributors(id, 5);
        assertEquals(1, contributors.size());
        assertEquals(company, contributors.getFirst().company());
        assertEquals("Iron Works", contributors.getFirst().name());
        assertNull(contributors.getFirst().soloWorker());
        assertEquals(100, contributors.getFirst().delivered());
        assertEquals(1, contributors.getFirst().chunks());
    }

    @Test
    void aShortDeliveryKeepsTheMoneyOfWhatWasDelivered() {
        var id = active();
        var chunk = order();
        offers.addChunk(id, chunk.id(), 100, 200.0, null, NOW);
        claim(chunk, company("Iron Works"));
        deliver(chunk, 25);
        state(chunk, OrderState.DELIVERED);

        assertTrue(offers.chunkDelivered(chunk.id(), NOW));

        var offer = offers.offer(id).orElseThrow();
        assertEquals(25, offer.delivered());
        assertEquals(50.0, offer.spent(), 0.001);
    }

    @Test
    void contributorsAreRankedByTheirItems() {
        var id = active();
        var iron = company("Iron Works");
        var stone = company("Stone Cutters");
        var worker = UUID.randomUUID();
        deliveredChunk(id, 50, iron, null);
        deliveredChunk(id, 50, iron, null);
        deliveredChunk(id, 70, stone, null);
        deliveredChunk(id, 120, 0, worker);

        var contributors = offers.contributors(id, 5);

        assertEquals(3, contributors.size());
        assertEquals(worker, contributors.get(0).soloWorker());
        assertEquals(0, contributors.get(0).company());
        assertEquals(120, contributors.get(0).delivered());
        assertEquals(iron, contributors.get(1).company());
        assertEquals(100, contributors.get(1).delivered());
        assertEquals(2, contributors.get(1).chunks());
        assertEquals("Stone Cutters", contributors.get(2).name());
        assertEquals(2, offers.contributors(id, 2).size());
        assertEquals(290, offers.offer(id).orElseThrow().delivered());
    }

    @Test
    void aChunkReturnsOnlyOnceItsOrderIsGone() {
        var id = active();
        var chunk = order();
        offers.addChunk(id, chunk.id(), 100, 250.0, null, NOW);

        assertFalse(offers.chunkReturned(chunk.id(), NOW));
        delete(chunk);
        assertEquals(1, offers.openChunks(id).size());
        assertNull(offers.openChunks(id).getFirst().state());
        assertTrue(offers.chunkReturned(chunk.id(), NOW));
        assertFalse(offers.chunkReturned(chunk.id(), NOW));

        assertEquals(0.0, offers.offer(id).orElseThrow().spent(), 0.001);
        assertTrue(offers.openChunks(id).isEmpty());
    }

    @Test
    void theWorkingOffersRunOrStillHaveOpenChunks() {
        var running = active();
        var closedWithChunk = active();
        var closed = active();
        offers.addChunk(closedWithChunk, order().id(), 100, 250.0, null, NOW);
        offers.state(closedWithChunk, OfferState.ACTIVE, OfferState.ENDED, NOW);
        offers.state(closed, OfferState.ACTIVE, OfferState.REACHED, NOW);

        assertEquals(
                List.of(running, closedWithChunk),
                offers.working().stream().map(EventOffer::id).toList());
    }

    @Test
    void chunksDoNotCountForTheMintedCapOfTheRules() {
        var chunk = order();
        var rule = order();
        orders.saveSystemDecision(new SystemDecision(chunk.id(), EventOffer.RULE, "OFFER", NOW, null, 250.0, "{}"));
        orders.saveSystemDecision(new SystemDecision(rule.id(), "iron", "DEFINED", NOW, null, 20.0, "{}"));

        assertEquals(20.0, orders.mintedSpent(NOW.minusHours(1)));
    }

    @Test
    void contestsAreStoredWithTheirPrizes() {
        var id = contest(100, 2.0, List.of(500.0, 250.5), 1000.0);

        var read = offers.offer(id).orElseThrow();
        assertEquals(OfferKind.CONTEST, read.kind());
        assertEquals(List.of(500.0, 250.5), read.prizes());
        assertEquals(750.5, read.prizeTotal());
        assertEquals(0.0, read.held());
        assertNull(read.started());
        assertNull(read.settled());
        assertEquals(List.of(), offers.offer(active()).orElseThrow().prizes());

        assertTrue(offers.activate(id, 1000.0, NOW));
        assertFalse(offers.activate(id, 1000.0, NOW));
        var started = offers.offer(id).orElseThrow();
        assertEquals(OfferState.ACTIVE, started.state());
        assertEquals(1000.0, started.held());
        assertEquals(NOW, started.started());
        assertEquals(List.of(id), offers.contests().stream().map(EventOffer::id).toList());
    }

    @Test
    void companiesJoinLeaveAndJoinAgainAndUseAnOrderSlotWhileTheContestRuns() {
        var id = contest(100, 2.0, List.of(), 200.0);
        var iron = company("Iron Works");
        var company = de.eldoria.companies.components.company.ISimpleCompany.forId(iron);

        assertEquals(0, orders.retrieveCompanySlotCount(company).join());
        assertTrue(offers.join(id, iron, NOW));
        assertFalse(offers.join(id, iron, NOW));
        assertEquals(0, orders.retrieveCompanySlotCount(company).join());
        assertEquals(List.of(id), offers.joined(iron));
        offers.activate(id, 0, NOW);
        assertEquals(1, orders.retrieveCompanySlotCount(company).join());
        assertEquals(0, orders.retrieveCompanyOrderCount(company).join());
        assertTrue(offers.leave(id, iron, NOW.plusHours(1)));
        assertFalse(offers.leave(id, iron, NOW.plusHours(1)));
        assertEquals(0, orders.retrieveCompanySlotCount(company).join());
        assertFalse(offers.entry(id, iron).orElseThrow().active());
        assertTrue(offers.join(id, iron, NOW.plusHours(2)));
        assertTrue(offers.entry(id, iron).orElseThrow().active());
        assertEquals(1, orders.retrieveCompanySlotCount(company).join());

        offers.state(id, OfferState.ACTIVE, OfferState.CANCELLED, NOW);
        assertEquals(0, orders.retrieveCompanySlotCount(company).join());
        assertTrue(offers.joined(iron).isEmpty());
        assertFalse(offers.join(id, company("Stone Cutters"), NOW));
    }

    @Test
    void roundedDeliveriesAddUpToTheWholePriceAndNeverDepleteEarly() {
        var id = contest(7, 0.334, List.of(), de.eldoria.companies.orders.OrderBuilder.total(0.334, 7));
        offers.activate(id, 0, NOW);
        var iron = company("Iron Works");
        offers.join(id, iron, NOW);

        var gross = 0.0;
        for (var delivery = 0; delivery < 7; delivery++) {
            var result = offers.deliver(id, iron, UUID.randomUUID(), 1, NOW);
            assertEquals(1, result.items(), "delivery " + delivery);
            gross += result.gross();
        }

        assertEquals(de.eldoria.companies.orders.OrderBuilder.total(0.334, 7), gross, 0.000_001);
        assertEquals(OfferState.REACHED, offers.offer(id).orElseThrow().state());
    }

    @Test
    void deliveriesAreCappedByTheMissingItemsAndTheMoneyLessThePrizes() {
        var id = contest(100, 2.0, List.of(50.0), 150.0);
        offers.activate(id, 0, NOW);
        var iron = company("Iron Works");
        var worker = UUID.randomUUID();

        assertEquals(
                de.eldoria.companies.data.wrapper.offer.ContestDelivery.Outcome.NOT_JOINED,
                offers.deliver(id, iron, worker, 10, NOW).outcome());
        offers.join(id, iron, NOW);
        var first = offers.deliver(id, iron, worker, 30, NOW);
        assertEquals(30, first.items());
        assertEquals(60.0, first.gross());
        assertEquals(OfferState.ACTIVE, first.state());

        var second = offers.deliver(id, iron, worker, 40, NOW.plusMinutes(1));
        assertEquals(20, second.items());
        assertEquals(OfferState.DEPLETED, second.state());
        assertEquals(
                de.eldoria.companies.data.wrapper.offer.ContestDelivery.Outcome.NOT_ACTIVE,
                offers.deliver(id, iron, worker, 1, NOW).outcome());

        var contest = offers.offer(id).orElseThrow();
        assertEquals(50, contest.delivered());
        assertEquals(100.0, contest.spent());
        assertEquals(NOW.plusMinutes(1), contest.closed());
        var entry = offers.entry(id, iron).orElseThrow();
        assertEquals(50, entry.delivered());
        assertEquals(100.0, entry.paid());
        assertEquals(2, count("contest_deliveries", id));
    }

    @Test
    void theLastItemsCloseTheContestAsReached() {
        var id = contest(10, 1.0, List.of(), 100.0);
        offers.activate(id, 0, NOW);
        var iron = company("Iron Works");
        offers.join(id, iron, NOW);

        var delivered = offers.deliver(id, iron, UUID.randomUUID(), 64, NOW);

        assertEquals(10, delivered.items());
        assertEquals(OfferState.REACHED, delivered.state());
        assertEquals(OfferState.REACHED, offers.offer(id).orElseThrow().state());
        assertEquals(List.of(id), offers.working().stream().map(EventOffer::id).toList());
    }

    @Test
    void twoCompaniesDeliveringTheLastItemsAtOnceNeverTakeMore() throws Exception {
        var id = contest(100, 1.0, List.of(), 1000.0);
        offers.activate(id, 0, NOW);
        var iron = company("Iron Works");
        var stone = company("Stone Cutters");
        offers.join(id, iron, NOW);
        offers.join(id, stone, NOW);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start = new java.util.concurrent.CountDownLatch(1);
            var first = pool.submit(() -> {
                start.await();
                return offers.deliver(id, iron, UUID.randomUUID(), 70, NOW);
            });
            var second = pool.submit(() -> {
                start.await();
                return offers.deliver(id, stone, UUID.randomUUID(), 70, NOW);
            });
            start.countDown();
            var taken = first.get().items() + second.get().items();

            var contest = offers.offer(id).orElseThrow();
            assertTrue(taken <= 100, "took " + taken);
            assertEquals(taken, contest.delivered());
            assertEquals(taken * 1.0, contest.spent(), 0.001);
            var ranking = offers.ranking(id);
            assertEquals(
                    taken, ranking.stream().mapToInt(entry -> entry.delivered()).sum());
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void theRankingPutsMostItemsFirstAndTheEarlierAmongEquals() {
        var id = contest(1000, 1.0, List.of(), 1000.0);
        offers.activate(id, 0, NOW);
        var iron = company("Iron Works");
        var stone = company("Stone Cutters");
        var wood = company("Wood Works");
        for (var company : List.of(iron, stone, wood)) offers.join(id, company, NOW);
        offers.deliver(id, stone, UUID.randomUUID(), 20, NOW.plusMinutes(2));
        offers.deliver(id, iron, UUID.randomUUID(), 20, NOW.plusMinutes(1));

        var ranking = offers.ranking(id);

        assertEquals(
                List.of(iron, stone, wood),
                ranking.stream().map(entry -> entry.company()).toList());
        assertEquals("Iron Works", ranking.getFirst().name());
    }

    @Test
    void aClosedContestIsSettledOnceWithItsPrizesAndRefund() {
        var id = contest(100, 1.0, List.of(30.0, 20.0), 150.0);
        offers.activate(id, 150.0, NOW);
        var iron = company("Iron Works");
        offers.state(id, OfferState.ACTIVE, OfferState.ENDED, NOW);
        var rows = List.of(
                new ContestPrize(1, 30.0, iron, null, PrizeState.PENDING),
                new ContestPrize(2, 20.0, null, null, PrizeState.NONE));

        assertTrue(offers.settle(id, rows, NOW));
        assertFalse(offers.settle(id, rows, NOW));
        assertEquals(30.0, offers.offer(id).orElseThrow().spent());
        assertEquals(PrizeState.PENDING, offers.prizes(id).get(0).state());

        assertTrue(offers.claimPrize(id, 1));
        assertFalse(offers.claimPrize(id, 1));
        assertFalse(offers.claimPrize(id, 2));
        assertTrue(offers.prizeOutcome(id, 1, true));
        assertFalse(offers.prizeOutcome(id, 1, true));
        assertEquals(List.of(id), offers.working().stream().map(EventOffer::id).toList());

        assertTrue(offers.refund(id, 120.0, NOW));
        assertFalse(offers.refund(id, 120.0, NOW));

        var settled = offers.offer(id).orElseThrow();
        assertEquals(NOW, settled.settled());
        assertEquals(30.0, settled.spent());
        assertEquals(120.0, settled.refund());
        assertEquals(NOW, settled.refunded());
        var prizes = offers.prizes(id);
        assertEquals(2, prizes.size());
        assertEquals(iron, prizes.get(0).company());
        assertEquals("Iron Works", prizes.get(0).name());
        assertTrue(prizes.get(0).paid());
        assertNull(prizes.get(1).company());
        assertEquals(PrizeState.NONE, prizes.get(1).state());
        assertTrue(offers.working().isEmpty());
    }

    @Test
    void aRefusedPrizeGivesItsMoneyBack() {
        var id = contest(100, 1.0, List.of(30.0), 130.0);
        offers.activate(id, 0, NOW);
        offers.state(id, OfferState.ACTIVE, OfferState.ENDED, NOW);
        offers.settle(id, List.of(new ContestPrize(1, 30.0, company("Iron Works"), null, PrizeState.PENDING)), NOW);

        assertFalse(offers.prizeOutcome(id, 1, false));
        assertTrue(offers.claimPrize(id, 1));
        assertTrue(offers.prizeOutcome(id, 1, false));

        assertEquals(0.0, offers.offer(id).orElseThrow().spent());
        assertEquals(PrizeState.FAILED, offers.prizes(id).getFirst().state());
    }

    private int contest(int amount, double unit, List<Double> prizes, double budget) {
        return offers.create(EventOffer.contest(
                        "Race",
                        OfferLine.material("OAK_LOG"),
                        amount,
                        unit,
                        prizes,
                        SourceType.MINTED,
                        null,
                        budget,
                        NOW,
                        NOW.plusDays(1),
                        "Admin",
                        NOW))
                .orElseThrow();
    }

    private static int count(String table, int offer) {
        return query("SELECT COUNT(1) AS count FROM " + table + " WHERE offer_id = ?")
                .single(call().bind(offer))
                .map(row -> row.getInt("count"))
                .first()
                .orElseThrow();
    }

    private int active() {
        var id = offers.create(goal("Logs", OfferLine.material("OAK_LOG"), SourceType.MINTED, null))
                .orElseThrow();
        offers.state(id, OfferState.SCHEDULED, OfferState.ACTIVE, NOW);
        return id;
    }

    private void deliveredChunk(int offer, int amount, int company, UUID worker) {
        var chunk = order();
        offers.addChunk(offer, chunk.id(), amount, amount * 2.0, null, NOW);
        if (worker == null) {
            claim(chunk, company);
        } else {
            query("UPDATE order_states SET solo_worker = ? WHERE id = ?")
                    .single(call().bind(worker, UUID_BYTES).bind(chunk.id()))
                    .update();
        }
        deliver(chunk, amount);
        state(chunk, OrderState.RECEIVED);
        assertTrue(offers.chunkDelivered(chunk.id(), NOW));
    }

    private static EventOffer goal(String name, OfferLine line, SourceType source, String account) {
        return EventOffer.goal(
                name,
                line,
                1000,
                2.5,
                100,
                3,
                source,
                account,
                2500.0,
                NOW,
                NOW.plusDays(3),
                "Admin",
                NOW.minusHours(1));
    }

    private SimpleOrder order() {
        var content = new OrderContent(0, new ItemStack(Material.OAK_LOG), false, 100, 250.0);
        orders.submitOrder(server, new SimpleOrder(SystemOwner.ID, "Logs", 0.0).toFullOrder(List.of(content)))
                .join();
        var placed = orders.retrieveOrdersByPlayer(server, OrderState.UNCLAIMED, OrderState.RECEIVED)
                .join();
        return placed.stream()
                .max((first, second) -> Integer.compare(first.id(), second.id()))
                .orElseThrow();
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

    private static void claim(SimpleOrder order, int company) {
        query("UPDATE order_states SET state = ?, company = ? WHERE id = ?")
                .single(call().bind(OrderState.CLAIMED.stateId()).bind(company).bind(order.id()))
                .update();
    }

    private static void state(SimpleOrder order, OrderState state) {
        query("UPDATE order_states SET state = ? WHERE id = ?")
                .single(call().bind(state.stateId()).bind(order.id()))
                .update();
    }

    private static void deliver(SimpleOrder order, int amount) {
        query("INSERT INTO order_deliveries(order_id, line, worker_uuid, stack, amount) VALUES (?, ?, ?, ?, ?)")
                .single(call().bind(order.id())
                        .bind(0)
                        .bind(UUID.randomUUID(), UUID_BYTES)
                        .bind("{\"type\":\"OAK_LOG\",\"amount\":1}")
                        .bind(amount))
                .insert();
    }

    private static void delete(SimpleOrder order) {
        for (var table : List.of("order_content", "order_states", "orders")) {
            query("DELETE FROM " + table + " WHERE id = ?")
                    .single(call().bind(order.id()))
                    .delete();
        }
    }
}
