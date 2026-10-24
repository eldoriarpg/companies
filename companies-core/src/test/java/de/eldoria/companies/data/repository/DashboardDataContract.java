/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.eldoria.companies.data.wrapper.dashboard.MarketDay;
import de.eldoria.companies.data.wrapper.dashboard.StoredKey;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.web.api.handshake.KeyKind;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What every dialect of the dashboard tables does. The database is started once per dialect and emptied before each
 * test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class DashboardDataContract {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 10, 0);

    private DataSource dataSource;
    private ADashboardData data;

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
     * @return the dialect's dashboard tables
     */
    protected abstract ADashboardData data();

    @BeforeAll
    void startDatabase() throws Exception {
        dataSource = start();
    }

    @BeforeEach
    void setUp() {
        QueryConfiguration.setDefault(QueryConfiguration.builder(dataSource).build());
        query("DELETE FROM dashboard_sessions").single().delete();
        query("DELETE FROM dashboard_key").single().delete();
        query("DELETE FROM market_history").single().delete();
        data = data();
    }

    @AfterAll
    void stopDatabase() {
        stop();
    }

    private static StoredSession session(String id, UUID player, LocalDateTime created) {
        return new StoredSession(
                id,
                player,
                "Wren",
                UUID.randomUUID(),
                created,
                created.plusMinutes(5),
                created,
                null,
                null,
                Set.of("companies.dashboard.use", "companies.orders.limit.5"));
    }

    @Test
    void theKeyIsStoredOnceAndReplaced() {
        assertEquals(Optional.empty(), data.key());

        data.saveKey(StoredKey.entered("lyd_test-key-one", "hash"), NOW);
        data.saveKey(new StoredKey("lyd_test-key-two", KeyKind.DASHBOARD_KEY, NOW.plusDays(7), NOW, "hash"), NOW);
        data.keyChecked(NOW.plusHours(1));

        var key = data.key().orElseThrow();
        assertEquals("lyd_test-key-two", key.secret());
        assertEquals(KeyKind.DASHBOARD_KEY, key.kind());
        assertEquals(NOW.plusDays(7), key.refreshAt());
        assertEquals(NOW.plusHours(1), key.checkedAt());
        assertEquals("hash", key.seedHash());
    }

    @Test
    void sessionsAreStoredOpenedOnceAndRemoved() {
        var player = UUID.randomUUID();
        data.saveSession(session("one", player, NOW));
        data.saveSession(session("two", player, NOW.plusMinutes(1)));

        assertTrue(data.open("one", NOW.plusMinutes(2), "Firefox on Android"));
        assertFalse(data.open("one", NOW.plusMinutes(3), "Chrome"));
        data.touch("one", NOW.plusMinutes(10));
        data.permissions(player, Set.of("companies.dashboard.use"));

        var one = data.session("one").orElseThrow();
        assertEquals(NOW.plusMinutes(2), one.opened());
        assertEquals(NOW.plusMinutes(10), one.lastActive());
        assertEquals(Optional.of("Firefox on Android"), one.deviceName());
        assertEquals(Set.of("companies.dashboard.use"), one.permissions());
        assertEquals(player, one.player());
        assertEquals(
                List.of("one", "two"),
                data.sessionsOf(player).stream().map(StoredSession::id).toList());
        assertEquals(Set.of(player), data.playersWithSessions());
        assertEquals(2, data.sessionCount());

        assertTrue(data.removeSession("two"));
        assertFalse(data.removeSession("two"));
        assertEquals(1, data.sessionCount());
    }

    @Test
    void endedSessionsAreIdleTooOldOrNeverOpened() {
        var player = UUID.randomUUID();
        data.saveSession(session("fresh", player, NOW));
        data.open("fresh", NOW, null);
        data.saveSession(session("idle", player, NOW.minusHours(2)));
        data.open("idle", NOW.minusHours(2), null);
        data.saveSession(session("pending", player, NOW.minusMinutes(10)));

        var ended = data.ended(NOW.minusMinutes(60), NOW.minusHours(24), NOW).stream()
                .map(StoredSession::id)
                .sorted()
                .toList();

        assertEquals(List.of("idle", "pending"), ended);
    }

    @Test
    void aMarketDayIsReplacedAsAWholeAndReadPerMaterial() {
        var day = LocalDate.of(2026, 10, 20);
        data.saveMarketDay(
                day,
                List.of(
                        new MarketDay(day, "STONE", 3, 30, 10.0, 8.0, 12.0),
                        new MarketDay(day, "DIRT", 1, 5, 2.0, 2.0, 2.0)));
        data.saveMarketDay(day.plusDays(1), List.of(new MarketDay(day.plusDays(1), "STONE", 2, 20, 11.0, 9.0, 13.0)));
        data.saveMarketDay(day, List.of(new MarketDay(day, "STONE", 4, 40, 10.5, 8.0, 12.0)));

        var stone = data.marketHistory("STONE", day);
        assertEquals(
                List.of(
                        new MarketDay(day, "STONE", 4, 40, 10.5, 8.0, 12.0),
                        new MarketDay(day.plusDays(1), "STONE", 2, 20, 11.0, 9.0, 13.0)),
                stone);
        assertTrue(data.marketHistory("DIRT", day).isEmpty());
        assertEquals(1, data.marketHistory("STONE", day.plusDays(1)).size());
        assertEquals(2, data.marketDays(day).size());
        assertEquals(Optional.of(day.plusDays(1)), data.lastMarketDay());
    }

    @Test
    void theMarketHistoryIsPurgedBeforeADay() {
        var day = LocalDate.of(2026, 10, 20);
        assertEquals(Optional.empty(), data.lastMarketDay());
        data.saveMarketDay(day, List.of(new MarketDay(day, "STONE", 3, 30, 10.0, 8.0, 12.0)));
        data.saveMarketDay(day.plusDays(2), List.of(new MarketDay(day.plusDays(2), "STONE", 3, 30, 10.0, 8.0, 12.0)));
        data.saveMarketDay(day.plusDays(3), List.of());

        assertEquals(1, data.purgeMarketHistory(day.plusDays(1)));
        assertEquals(1, data.marketDays(day).size());
    }
}
