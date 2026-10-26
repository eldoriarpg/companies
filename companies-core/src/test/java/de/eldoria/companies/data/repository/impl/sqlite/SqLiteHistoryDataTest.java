/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.components.order.HistoryOutcome;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.history.HistoryEntry;
import de.eldoria.companies.data.wrapper.history.HistorySnapshot;
import de.eldoria.companies.data.wrapper.order.DeliveredStack;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.data.wrapper.order.SystemDecision;
import de.eldoria.companies.data.wrapper.stats.PayoutRecord;
import de.eldoria.companies.orders.HistoryRecorder;
import de.eldoria.companies.orders.SystemOwner;
import de.eldoria.companies.util.TestExecutors;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

class SqLiteHistoryDataTest {
    private static final LocalDateTime PLACED = LocalDateTime.of(2026, 2, 20, 8, 30, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 1, 12, 0, 0);
    private static final int COMPANY = 7;

    private final UUID owner = UUID.randomUUID();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @TempDir
    Path directory;

    private ExecutorService executor;
    private SqLiteHistoryData history;
    private HistoryRecorder recorder;

    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(4);
        history = new SqLiteHistoryData(SqLiteTestDatabase.mapper(), executor);
        recorder = new HistoryRecorder(history, Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneId.of("UTC")));
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
    }

    private FullOrder order(int id) {
        return order(id, owner);
    }

    private FullOrder order(int id, UUID owner) {
        var sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        var swords = new OrderContent(0, sword, true, 2, 300.0);
        swords.deliveries(List.of(new DeliveredStack(1, alice, sword, 2)));
        var stone = new OrderContent(1, new ItemStack(Material.STONE), false, 10, 50.0);
        stone.deliveries(List.of(
                new DeliveredStack(2, alice, new ItemStack(Material.STONE), 4),
                new DeliveredStack(3, bob, new ItemStack(Material.STONE), 6)));
        return new SimpleOrder(id, owner, "Tools", PLACED, COMPANY, PLACED, OrderState.DELIVERED, 12.5, 0, 0)
                .toFullOrder(List.of(swords, stone));
    }

    private HistoryEntry entry(long id) {
        return history.retrieveEntry(id).join().orElseThrow();
    }

    @Test
    void anOwnerEntryKeepsLinesPriceAndFeeButNoCompany() {
        var id = recorder.ownerEntry(order(1), HistoryOutcome.RECEIVED).join().orElseThrow();

        var entry = entry(id);

        Assertions.assertEquals(owner, entry.owner());
        Assertions.assertNull(entry.company());
        Assertions.assertEquals(HistoryOutcome.RECEIVED, entry.outcome());
        Assertions.assertEquals("Tools", entry.name());
        Assertions.assertEquals(PLACED, entry.placed());
        Assertions.assertEquals(NOW, entry.finished());
        var snapshot = entry.snapshot();
        Assertions.assertEquals(350.0, snapshot.price());
        Assertions.assertEquals(12.5, snapshot.fee());
        Assertions.assertEquals(List.of(), snapshot.workers());
        Assertions.assertEquals(0.0, snapshot.net());
        var swords = snapshot.lines().getFirst();
        Assertions.assertEquals(5, swords.template().getEnchantmentLevel(Enchantment.SHARPNESS));
        Assertions.assertTrue(swords.exact());
        Assertions.assertEquals(2, swords.delivered());
        Assertions.assertEquals(10, snapshot.lines().get(1).delivered());
    }

    @Test
    void aCompanyEntryKeepsPayoutsAndWorkersButNoOwner() {
        var payouts = List.of(
                new PayoutRecord(1, COMPANY, alice, 6, 320.0, 32.0, 288.0, true, NOW),
                new PayoutRecord(1, COMPANY, bob, 6, 30.0, 3.0, 27.0, false, NOW));

        var id = recorder.companyEntry(order(1), COMPANY, HistoryOutcome.DELIVERED, payouts)
                .join()
                .orElseThrow();

        var entry = entry(id);
        Assertions.assertNull(entry.owner());
        Assertions.assertEquals(COMPANY, entry.company());
        var snapshot = entry.snapshot();
        Assertions.assertEquals(0.0, snapshot.fee());
        Assertions.assertEquals(320.0, snapshot.gross());
        Assertions.assertEquals(32.0, snapshot.tax());
        Assertions.assertEquals(288.0, snapshot.net());
        Assertions.assertEquals(
                List.of(
                        new HistorySnapshot.Worker(alice, 6, 320.0, 288.0),
                        new HistorySnapshot.Worker(bob, 6, 0.0, 0.0)),
                snapshot.workers());
    }

    @Test
    void withoutPayoutsTheWorkersComeFromTheDeliveries() {
        var id = recorder.companyEntry(order(1), COMPANY, HistoryOutcome.ABORTED, List.of())
                .join()
                .orElseThrow();

        var workers = entry(id).snapshot().workers();

        Assertions.assertEquals(2, workers.size());
        Assertions.assertEquals(
                6,
                workers.stream()
                        .filter(worker -> worker.worker().equals(alice))
                        .findFirst()
                        .orElseThrow()
                        .items());
        Assertions.assertTrue(workers.stream().allMatch(worker -> worker.net() == 0.0));
    }

    @Test
    void eachSideOnlySeesItsOwnEntries() {
        recorder.ownerEntry(order(1), HistoryOutcome.RECEIVED).join();
        recorder.companyEntry(order(1), COMPANY, HistoryOutcome.DELIVERED, List.of())
                .join();
        recorder.companyEntry(order(2), COMPANY + 1, HistoryOutcome.EXPIRED, List.of())
                .join();

        var ownerSide = history.retrieveOwnerHistory(owner, 1, 10).join();
        var companySide = history.retrieveCompanyHistory(COMPANY, 1, 10).join();

        Assertions.assertEquals(1, ownerSide.size());
        Assertions.assertTrue(ownerSide.stream().allMatch(entry -> entry.company() == null));
        Assertions.assertEquals(1, companySide.size());
        Assertions.assertTrue(companySide.stream().allMatch(entry -> entry.owner() == null));
        Assertions.assertEquals(2, history.retrieveByOrder(1).join().size());
    }

    @Test
    void theTableRefusesEntriesWithBothOrNoSide() {
        var both = "INSERT INTO order_history(order_id, owner_uuid, company_id, outcome, order_name, placed, finished,"
                + " snapshot) VALUES (1, ?, 1, 4, 'x', '2026-01-01 00:00:00', '2026-01-01 00:00:00', '{}')";
        var none = "INSERT INTO order_history(order_id, owner_uuid, company_id, outcome, order_name, placed, finished,"
                + " snapshot) VALUES (1, NULL, NULL, 4, 'x', '2026-01-01 00:00:00', '2026-01-01 00:00:00', '{}')";

        query(both).single(call().bind(owner, UUID_BYTES)).insert();
        query(none).single().insert();

        Assertions.assertEquals(0, SqLiteTestDatabase.count("order_history", "TRUE"));
    }

    @Test
    void pagesAreLatestFirst() {
        var clock = NOW;
        for (var order = 1; order <= 5; order++) {
            var finished = clock.plusHours(order);
            history.submitEntry(HistoryEntry.ofOwner(
                            order,
                            owner,
                            HistoryOutcome.CANCELLED,
                            "Order " + order,
                            PLACED,
                            finished,
                            new HistorySnapshot(List.of(), 1, 0, 0, 0, 0, List.of())))
                    .join();
        }

        var page = history.retrieveOwnerHistory(owner, 2, 2).join();

        Assertions.assertEquals(
                List.of(3, 2), page.stream().map(HistoryEntry::order).toList());
        Assertions.assertEquals(
                List.of(), history.retrieveOwnerHistory(UUID.randomUUID(), 1, 2).join());
    }

    @Test
    void retentionDeletesEntriesThatEndedBefore() {
        var old = new HistorySnapshot(List.of(), 1, 0, 0, 0, 0, List.of());
        history.submitEntry(HistoryEntry.ofCompany(
                        1, COMPANY, HistoryOutcome.EXPIRED, "old", PLACED, NOW.minusDays(100), old))
                .join();
        var kept = history.submitEntry(HistoryEntry.ofCompany(
                        2, COMPANY, HistoryOutcome.EXPIRED, "new", PLACED, NOW.minusDays(10), old))
                .join()
                .orElseThrow();

        Assertions.assertEquals(1, history.submitPurge(NOW.minusDays(90)).join());
        Assertions.assertEquals(
                List.of(kept),
                history.retrieveCompanyHistory(COMPANY, 1, 10).join().stream()
                        .map(HistoryEntry::id)
                        .toList());
        Assertions.assertTrue(history.retrieveEntry(kept + 1).join().isEmpty());
    }

    @Test
    void companyEntriesLeaveOutTheOrdersOfTheServer() {
        var day = NOW.toLocalDate().atStartOfDay();
        var player = recorder.companyEntry(order(1), COMPANY, HistoryOutcome.DELIVERED, List.of())
                .join()
                .orElseThrow();
        var server = order(2, SystemOwner.ID);
        recorder.companyEntry(server, COMPANY, HistoryOutcome.DELIVERED, List.of())
                .join();
        recorder.ownerEntry(server, HistoryOutcome.RECEIVED).join();

        var entries = history.retrieveCompanyEntries(HistoryOutcome.DELIVERED, day, day.plusDays(1))
                .join();

        Assertions.assertEquals(
                List.of(player), entries.stream().map(HistoryEntry::id).toList());
    }

    @Test
    void thePurgeDropsOldDecisionsOfOrdersThatAreGone() {
        var orderData = new SqLiteOrderData(null, SqLiteTestDatabase.mapper());
        orderData.saveSystemDecision(new SystemDecision(1, "iron", "DEFINED", NOW.minusDays(100), null, 10, "{}"));
        orderData.saveSystemDecision(new SystemDecision(2, "iron", "DEFINED", NOW.minusDays(10), null, 10, "{}"));

        history.submitPurge(NOW.minusDays(90)).join();

        Assertions.assertTrue(orderData.systemDecision(1).isEmpty());
        Assertions.assertTrue(orderData.systemDecision(2).isPresent());
    }

    @Test
    void companyEntriesAreReadPerOutcomeAndSpanWithTheEndLeftOut() {
        var day = NOW.toLocalDate().atStartOfDay();
        var delivered = recorder.companyEntry(order(1), COMPANY, HistoryOutcome.DELIVERED, List.of())
                .join()
                .orElseThrow();
        recorder.companyEntry(order(2), COMPANY, HistoryOutcome.ABORTED, List.of())
                .join();
        recorder.ownerEntry(order(3), HistoryOutcome.RECEIVED).join();

        var inDay = history.retrieveCompanyEntries(HistoryOutcome.DELIVERED, day, day.plusDays(1))
                .join();
        var endingAtNoon = history.retrieveCompanyEntries(HistoryOutcome.DELIVERED, day, NOW)
                .join();
        var startingAtNoon = history.retrieveCompanyEntries(HistoryOutcome.DELIVERED, NOW, day.plusDays(1))
                .join();

        Assertions.assertEquals(
                List.of(delivered), inDay.stream().map(HistoryEntry::id).toList());
        Assertions.assertTrue(endingAtNoon.isEmpty());
        Assertions.assertEquals(1, startingAtNoon.size());
    }
}
