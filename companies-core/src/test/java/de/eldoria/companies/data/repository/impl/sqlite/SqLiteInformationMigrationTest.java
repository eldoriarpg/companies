/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.chojo.sadu.core.updater.SqlVersion;
import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.components.ranking.TopOrder;
import de.eldoria.companies.data.wrapper.inbox.InboxEntry;
import de.eldoria.companies.data.wrapper.stats.MemberStats;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * Upgrades a database with stored notifications and orders in every state from the previous version and checks the
 * inbox, the backfilled payout ledger and the statistics view.
 */
class SqLiteInformationMigrationTest {
    private static final LocalDateTime DELIVERED_AT = LocalDateTime.of(2026, 2, 10, 9, 0, 0);
    private static final LocalDateTime RECEIVED_AT = LocalDateTime.of(2026, 2, 15, 18, 30, 0);

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();

    @TempDir
    Path directory;

    private ExecutorService executor;
    private SqLiteInboxData inbox;
    private SqLiteStatsData stats;
    private int miners;
    private int farmers;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory, new SqlVersion(1, 5));
        miners = insertCompany("Miners");
        farmers = insertCompany("Farmers");
        insertNotification(alice, "2026-02-01 10:00:00", "{\"message\":\"<red>First\"}");
        insertNotification(alice, "2026-02-02 11:00:00", "{\"message\":\"Second\"}");
        insertNotification(bob, "2026-02-03 12:00:00", "{\"message\":\"Other\"}");

        var delivered = insertOrder(miners, 200, DELIVERED_AT);
        insertContent(delivered, 0, 10, 100.0);
        insertContent(delivered, 1, 4, 40.0);
        insertDelivery(delivered, 0, alice, 6);
        insertDelivery(delivered, 0, bob, 4);
        insertDelivery(delivered, 1, alice, 4);
        var received = insertOrder(farmers, 300, RECEIVED_AT);
        insertContent(received, 0, 5, 50.0);
        insertDelivery(received, 0, carol, 5);
        var claimed = insertOrder(miners, 100, RECEIVED_AT);
        insertContent(claimed, 0, 8, 80.0);
        insertDelivery(claimed, 0, alice, 2);
        var disbanded = insertOrder(99, 200, RECEIVED_AT);
        insertContent(disbanded, 0, 3, 30.0);
        insertDelivery(disbanded, 0, bob, 3);

        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(4);
        inbox = new SqLiteInboxData(executor);
        stats = new SqLiteStatsData(executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private static int insertCompany(String name) {
        return query("INSERT INTO companies(name) VALUES (?) RETURNING id")
                .single(call().bind(name))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    private static void insertNotification(UUID player, String created, String data) {
        query("INSERT INTO company_notification(user_uuid, created, notification_data) VALUES (?, ?, ?)")
                .single(call().bind(player, UUID_BYTES).bind(created).bind(data))
                .insert();
    }

    private static int insertOrder(int company, int state, LocalDateTime lastUpdate) {
        var id = query("INSERT INTO orders(owner_uuid, name) VALUES (?, 'legacy') RETURNING id")
                .single(call().bind(UUID.randomUUID(), UUID_BYTES))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
        query("INSERT INTO order_states(id, company, state, last_update) VALUES (?, ?, ?, ?)")
                .single(call().bind(id).bind(company).bind(state).bind(SqLiteAdapter.format(lastUpdate)))
                .insert();
        return id;
    }

    private static void insertContent(int order, int line, int amount, double price) {
        query(
                        "INSERT INTO order_content(id, line, material, stack, exact, amount, price) VALUES (?, ?, 'STONE', '{}', 0, ?, ?)")
                .single(call().bind(order).bind(line).bind(amount).bind(price))
                .insert();
    }

    private static void insertDelivery(int order, int line, UUID worker, int amount) {
        query("INSERT INTO order_deliveries(order_id, line, worker_uuid, stack, amount) VALUES (?, ?, ?, '{}', ?)")
                .single(call().bind(order).bind(line).bind(worker, UUID_BYTES).bind(amount))
                .insert();
    }

    private Map<UUID, MemberStats> memberStats(int company, LocalDateTime since) {
        return stats.retrieveMemberStats(company, since).join().stream()
                .collect(Collectors.toMap(MemberStats::worker, Function.identity()));
    }

    @Test
    void storedNotificationsBecomePendingLegacyEntries() {
        var entries = inbox.retrievePending(List.of(alice), 10).join();

        Assertions.assertEquals(2, entries.size());
        Assertions.assertTrue(entries.stream().allMatch(entry -> entry.type() == NotificationType.LEGACY));
        Assertions.assertTrue(entries.stream().noneMatch(InboxEntry::read));
        Assertions.assertEquals(
                LocalDateTime.of(2026, 2, 1, 10, 0, 0), entries.getFirst().created());
        Assertions.assertEquals(
                List.of("<red>First", "Second"),
                entries.stream()
                        .map(entry -> entry.args()
                                .values()
                                .get(NotificationArgs.LEGACY_MESSAGE)
                                .value())
                        .toList());
        Assertions.assertEquals(
                1, inbox.retrievePending(List.of(bob), 10).join().size());
        Assertions.assertEquals(2, inbox.retrieveUnreadCount(alice, 0).join());
    }

    @Test
    void theOldNotificationTableIsGone() {
        Assertions.assertEquals(
                0, SqLiteTestDatabase.count("sqlite_master", "type = 'table' AND name = 'company_notification'"));
        Assertions.assertEquals(
                3,
                SqLiteTestDatabase.count(
                        "sqlite_master",
                        "type = 'table' AND name IN ('notification_settings', 'order_payouts', 'order_history')"));
        Assertions.assertEquals(0, SqLiteTestDatabase.count("order_history", "TRUE"));
    }

    @Test
    void payoutsAreBackfilledFromCompletedOrdersOnly() {
        Assertions.assertEquals(3, SqLiteTestDatabase.count("order_payouts", "TRUE"));
        Assertions.assertEquals(3, SqLiteTestDatabase.count("order_payouts", "tax = 0 AND net = gross AND paid = 1"));

        var minersStats = memberStats(miners, null);
        Assertions.assertEquals(2, minersStats.size());
        Assertions.assertEquals(10, minersStats.get(alice).items());
        Assertions.assertEquals(100.0, minersStats.get(alice).earned(), 1e-6);
        Assertions.assertEquals(1, minersStats.get(alice).orders());
        Assertions.assertEquals(4, minersStats.get(bob).items());
        Assertions.assertEquals(40.0, minersStats.get(bob).earned(), 1e-6);
        Assertions.assertEquals(50.0, memberStats(farmers, null).get(carol).earned(), 1e-6);
    }

    @Test
    void backfilledPayoutsAreDatedByTheirLastUpdate() {
        Assertions.assertEquals(2, memberStats(miners, DELIVERED_AT).size());
        Assertions.assertEquals(
                0, memberStats(miners, DELIVERED_AT.plusSeconds(1)).size());
        Assertions.assertEquals(1, memberStats(farmers, RECEIVED_AT).size());
    }

    @Test
    void theStatisticsViewHasLevelTagAndColour() {
        query("UPDATE companies SET level = 3, tag = 'MIN', color = 'gold' WHERE id = ?")
                .single(call().bind(miners))
                .update();

        var ranks = stats.retrieveRanking(TopOrder.LEVEL, TimeRange.ALL, RECEIVED_AT, 1, 10)
                .join();

        Assertions.assertEquals(miners, ranks.getFirst().id());
        Assertions.assertEquals(3, ranks.getFirst().level());
        Assertions.assertEquals("MIN", ranks.getFirst().tag().orElseThrow());
        Assertions.assertEquals("gold", ranks.getFirst().color().orElseThrow());
        Assertions.assertEquals(140.0, ranks.getFirst().price(), 1e-6);
        Assertions.assertEquals(1, ranks.getFirst().orderCount());
    }

    @Test
    void backfilledPayoutsRankInTheirWindow() {
        var ranks = stats.retrieveRanking(TopOrder.EARNED, TimeRange.LAST_7_DAYS, RECEIVED_AT.plusDays(3), 1, 10)
                .join();

        Assertions.assertEquals(
                List.of(farmers), ranks.stream().map(rank -> rank.id()).toList());
    }
}
