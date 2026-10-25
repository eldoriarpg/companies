/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.sessions;

import de.eldoria.companies.configuration.elements.DashboardSettings;
import de.eldoria.companies.dashboard.MutableClock;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteDashboardData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.session.CloseReason;
import de.eldoria.companies.web.api.session.PlayerRef;
import de.eldoria.companies.web.api.session.SessionCreated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardSessionsTest {
    private static final Instant START = Instant.parse("2026-10-24T10:00:00Z");
    private static final UUID NODE = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @TempDir
    Path directory;

    private final MutableClock clock = new MutableClock(START);
    private final PlayerRef wren = new PlayerRef(UUID.randomUUID(), "Wren");
    private DashboardSessions sessions;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        sessions = new DashboardSessions(new SqLiteDashboardData(), DashboardSettings::new, NODE, clock);
    }

    private void create(String id) {
        sessions.created(
                new SessionCreated(
                        null,
                        Protocol.VERSION,
                        id,
                        "AB12CD",
                        "KPMWQR",
                        "https://dash.example/AB12CD",
                        clock.instant().plus(Duration.ofMinutes(5))),
                wren,
                Set.of("companies.dashboard.use"));
    }

    @Test
    void aPendingSessionAnswersNothingUntilItIsOpened() {
        create("one");

        assertTrue(sessions.active("one", wren.uuid()).isEmpty());
        assertTrue(sessions.open("one", wren.uuid(), "Firefox").isPresent());
        assertTrue(sessions.active("one", wren.uuid()).isPresent());
        assertTrue(sessions.open("one", wren.uuid(), "Firefox").isEmpty());
        assertTrue(sessions.active("one", UUID.randomUUID()).isEmpty());
        assertTrue(sessions.active("invented", wren.uuid()).isEmpty());
    }

    @Test
    void anExpiredCodeOpensNothing() {
        create("one");
        clock.advance(Duration.ofMinutes(6));

        assertTrue(sessions.open("one", wren.uuid(), "Firefox").isEmpty());
        assertEquals(
                List.of(CloseReason.EXPIRED),
                sessions.sweep().stream().map(DashboardSessions.Ended::reason).toList());
        assertTrue(sessions.of(wren.uuid()).isEmpty());
    }

    @Test
    void anIdleSessionEnds() {
        create("one");
        sessions.open("one", wren.uuid(), null);
        clock.advance(Duration.ofMinutes(61));

        assertTrue(sessions.active("one", wren.uuid()).isEmpty());
        var ended = sessions.sweep();
        assertEquals(1, ended.size());
        assertEquals(CloseReason.IDLE, ended.getFirst().reason());
        assertTrue(sessions.sweep().isEmpty());
    }

    @Test
    void aWatchedSessionIsNotIdle() {
        create("one");
        sessions.open("one", wren.uuid(), null);
        clock.advance(Duration.ofMinutes(40));
        sessions.watched(Set.of("one", "unknown"));
        clock.advance(Duration.ofMinutes(40));

        assertTrue(sessions.sweep().isEmpty());
        assertTrue(sessions.active("one", wren.uuid()).isPresent());
    }

    @Test
    void aUsedSessionEndsAtItsMaximumAge() {
        create("one");
        sessions.open("one", wren.uuid(), null);
        for (var hour = 0; hour < 24; hour++) {
            clock.advance(Duration.ofMinutes(50));
            assertTrue(sessions.active("one", wren.uuid()).isPresent());
            clock.advance(Duration.ofMinutes(10));
        }
        clock.advance(Duration.ofMinutes(1));

        assertEquals(
                List.of(CloseReason.EXPIRED),
                sessions.sweep().stream().map(DashboardSessions.Ended::reason).toList());
    }

    @Test
    void endingAllSessionsReportsEachOnce() {
        create("one");
        create("two");

        assertEquals(2, sessions.endAll(wren.uuid()).size());
        assertTrue(sessions.endAll(wren.uuid()).isEmpty());
        assertEquals(0, sessions.count());
    }
}
