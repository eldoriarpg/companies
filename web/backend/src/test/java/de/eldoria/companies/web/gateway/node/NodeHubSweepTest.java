/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.node;

import de.eldoria.companies.web.api.handshake.GatewayKind;
import de.eldoria.companies.web.api.link.ErrorCode;
import de.eldoria.companies.web.gateway.RateLimiter;
import de.eldoria.companies.web.gateway.Socket;
import de.eldoria.companies.web.gateway.policy.VersionPolicy;
import de.eldoria.companies.web.gateway.storage.IconRepository;
import de.eldoria.companies.web.gateway.storage.ServerRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The sweep with sockets that never report their close, as a misbehaving or malicious client may leave them.
 */
class NodeHubSweepTest {
    private final Ticking clock = new Ticking();
    private final NodeHub hub = new NodeHub(
            credentials -> new Authentication.Refused(ErrorCode.UNAUTHORIZED, "no"),
            VersionPolicy.bundled(),
            GatewayKind.SELF_HOSTED,
            new ServerRepository(),
            new IconRepository(),
            new RateLimiter(100, Duration.ofMinutes(1), clock),
            clock);

    @Test
    void closedLinksDoNotStopTheSweep() {
        List<Silent> early = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Silent socket = new Silent();
            early.add(socket);
            hub.connect("early-" + i, socket, "198.51.100.1");
        }
        clock.advance(Duration.ofSeconds(11));
        hub.sweep();
        early.forEach(socket -> assertEquals(List.of(NodeHub.CLOSE_IDLE), socket.closes));

        Silent late = new Silent();
        hub.connect("late", late, "198.51.100.2");
        clock.advance(Duration.ofSeconds(31));
        hub.sweep();
        assertEquals(List.of(NodeHub.CLOSE_IDLE), late.closes);

        clock.advance(Duration.ofSeconds(60));
        hub.sweep();
        hub.sweep();
        early.forEach(socket -> assertEquals(1, socket.closes.size()));
    }

    /**
     * A socket whose close never comes back as a close event.
     */
    private static final class Silent implements Socket {
        private final List<Integer> closes = new ArrayList<>();

        @Override
        public void send(String text) {}

        @Override
        public void close(int code, String reason) {
            closes.add(code);
        }

        @Override
        public void limit(long bytes) {}

        @Override
        public boolean isOpen() {
            return closes.isEmpty();
        }
    }

    private static final class Ticking extends Clock {
        private Instant now = Instant.parse("2026-10-24T12:00:00Z");

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
