/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.keys;

import de.eldoria.companies.dashboard.MutableClock;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteDashboardData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.dashboard.StoredKey;
import de.eldoria.companies.web.api.handshake.KeyKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardKeysTest {
    private static final String KEY = "lyd_test-key-one";
    private static final String NEXT = "lyd_test-key-two";
    private static final Instant START = Instant.parse("2026-10-24T10:00:00Z");

    @TempDir
    Path directory;

    private final MutableClock clock = new MutableClock(START);
    private final StringBuilder log = new StringBuilder();
    private FakeLyna lyna;
    private SqLiteDashboardData data;
    private DashboardKeys keys;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        lyna = FakeLyna.start();
        data = new SqLiteDashboardData();
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                log.append(record.getMessage()).append('\n');
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        keys = new DashboardKeys(data, new HttpLynaKeys(lyna.address(), "Companies/test"), clock, logger);
    }

    @AfterEach
    void tearDown() {
        lyna.close();
    }

    @Test
    void anEnteredKeyIsStoredForTheNetworkAndTellsItsKind() {
        keys.enter(" " + KEY + " ");
        assertEquals(KEY, data.key().orElseThrow().secret());
        assertEquals(KeyKind.DASHBOARD_KEY, data.key().orElseThrow().kind());

        keys.enter("self-hosted-token");
        assertEquals(KeyKind.SERVER_TOKEN, data.key().orElseThrow().kind());
        assertFalse(log.toString().contains("self-hosted-token"));
        assertFalse(log.toString().contains(KEY));
    }

    @Test
    void aConfiguredKeyIsStoredOnceAndDoesNotUndoARotation() {
        assertTrue(keys.seed(Optional.of(KEY)));
        lyna.answer(200, "{\"key\":\"" + NEXT + "\",\"refreshAt\":\"2026-10-31T10:00:00Z\"}");
        keys.refreshIfDue();
        assertEquals(NEXT, data.key().orElseThrow().secret());

        assertFalse(keys.seed(Optional.of(KEY)));
        assertEquals(NEXT, data.key().orElseThrow().secret());

        assertTrue(keys.seed(Optional.of("lyd_test-key-three")));
        assertEquals("lyd_test-key-three", data.key().orElseThrow().secret());
        assertFalse(keys.seed(Optional.empty()));
    }

    @Test
    void theRefreshAsksLynaWithTheKeyAndStoresTheNextKeyBeforeItIsUsed() {
        keys.enter(KEY);
        lyna.answer(200, "{\"key\":\"" + NEXT + "\",\"refreshAt\":\"2026-10-31T10:00:00Z\"}");

        var used = keys.refreshIfDue().orElseThrow();

        assertEquals(NEXT, used.secret());
        var stored = data.key().orElseThrow();
        assertEquals(NEXT, stored.secret());
        assertEquals(LocalDateTime.of(2026, 10, 31, 10, 0), stored.refreshAt());
        assertEquals(LocalDateTime.ofInstant(START, ZoneOffset.UTC), stored.checkedAt());
        assertEquals(List.of(new FakeLyna.Request("POST", "Bearer " + KEY, "Companies/test")), lyna.requests());
        assertTrue(log.toString().contains("rotated"));
        assertFalse(log.toString().contains(NEXT));
    }

    @Test
    void lynaIsAskedOnceADay() {
        keys.enter(KEY);
        lyna.answer(200, "{\"key\":\"" + KEY + "\",\"refreshAt\":\"2026-10-31T10:00:00Z\"}");
        keys.refreshIfDue();
        clock.advance(Duration.ofHours(23));
        keys.refreshIfDue();
        assertEquals(1, lyna.requests().size());

        clock.advance(Duration.ofHours(1));
        keys.refreshIfDue();
        assertEquals(2, lyna.requests().size());
        assertFalse(log.toString().contains("rotated"));
    }

    @Test
    void aRefusedKeyIsKeptAndReportedOnce() {
        keys.enter(KEY);
        lyna.answer(401, "{\"reason\":\"EXPIRED\"}");

        var used = keys.refreshIfDue().orElseThrow();
        clock.advance(Duration.ofDays(1));
        keys.refreshIfDue();

        assertEquals(KEY, used.secret());
        assertEquals(Optional.of("EXPIRED"), keys.problem());
        assertEquals(1, log.toString().split("EXPIRED", -1).length - 1);
        assertTrue(data.key().orElseThrow().checked().isPresent());
    }

    @Test
    void noAccessIsReportedAndTooSoonIsNot() {
        keys.enter(KEY);
        lyna.answer(429, "{\"reason\":\"TOO_SOON\"}");
        keys.refreshIfDue();
        assertEquals(Optional.empty(), keys.problem());
        assertTrue(data.key().orElseThrow().checked().isPresent());

        clock.advance(Duration.ofDays(1));
        lyna.answer(403, "{\"reason\":\"NO_ACCESS\"}");
        keys.refreshIfDue();
        assertEquals(Optional.of("NO_ACCESS"), keys.problem());
    }

    @Test
    void aFailedRequestIsTriedAgainOnTheNextCheck() {
        keys.enter(KEY);
        lyna.answer(502, "bad gateway");
        keys.refreshIfDue();
        assertTrue(data.key().orElseThrow().checked().isEmpty());

        lyna.answer(200, "{\"key\":\"" + NEXT + "\",\"refreshAt\":1793440800}");
        keys.refreshIfDue();
        assertEquals(2, lyna.requests().size());
        assertEquals(NEXT, data.key().orElseThrow().secret());
    }

    @Test
    void aServerTokenIsNeverRefreshed() {
        keys.enter("self-hosted-token");
        clock.advance(Duration.ofDays(3));

        assertEquals(
                "self-hosted-token", keys.refreshIfDue().map(StoredKey::secret).orElseThrow());
        assertTrue(lyna.requests().isEmpty());
    }

    @Test
    void withoutAKeyNothingIsAsked() {
        assertTrue(keys.refreshIfDue().isEmpty());
        assertTrue(lyna.requests().isEmpty());
    }
}
