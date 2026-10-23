/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkPresenceTest {
    private static final UUID LOBBY = UUID.randomUUID();
    private static final UUID SURVIVAL = UUID.randomUUID();
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    private final SteppedClock clock = new SteppedClock();
    private final NetworkPresence presence = new NetworkPresence(clock);

    @Test
    void joinsAndQuitsMovePlayersBetweenNodes() {
        presence.presence(LOBBY, ALICE, true);
        presence.presence(SURVIVAL, ALICE, true);
        presence.presence(LOBBY, ALICE, false);

        assertEquals(Optional.of(SURVIVAL), presence.nodeOf(ALICE));

        presence.presence(SURVIVAL, ALICE, false);

        assertEquals(Optional.empty(), presence.nodeOf(ALICE));
    }

    @Test
    void aHeartbeatReplacesThePlayersOfItsNode() {
        presence.presence(LOBBY, ALICE, true);
        presence.presence(SURVIVAL, BOB, true);

        presence.heartbeat(LOBBY, List.of(BOB));

        assertEquals(Optional.empty(), presence.nodeOf(ALICE));
        assertEquals(Optional.of(LOBBY), presence.nodeOf(BOB));
    }

    @Test
    void silentAndStoppedNodesAreGoneWithTheirPlayers() {
        presence.heartbeat(LOBBY, List.of(ALICE));
        presence.heartbeat(SURVIVAL, List.of(BOB));

        presence.stopped(SURVIVAL);
        assertFalse(presence.alive(SURVIVAL));
        assertEquals(Optional.empty(), presence.nodeOf(BOB));

        clock.advance(NetworkPresence.TIMEOUT.plusSeconds(1));
        assertFalse(presence.alive(LOBBY));
        assertEquals(Optional.empty(), presence.nodeOf(ALICE));

        presence.expire();
        presence.heartbeat(SURVIVAL, List.of());
        assertTrue(presence.alive(SURVIVAL));
        assertEquals(Optional.empty(), presence.nodeOf(ALICE));
    }

    @Test
    void clearForgetsEverything() {
        presence.heartbeat(LOBBY, List.of(ALICE));

        presence.clear();

        assertFalse(presence.alive(LOBBY));
        assertEquals(Optional.empty(), presence.nodeOf(ALICE));
    }

    private static final class SteppedClock extends Clock {
        private Instant now = Instant.parse("2026-03-01T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
