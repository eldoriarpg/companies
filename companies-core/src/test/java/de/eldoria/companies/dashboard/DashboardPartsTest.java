/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard;

import de.eldoria.companies.configuration.elements.DashboardSettings;
import de.eldoria.companies.dashboard.gateway.Backoff;
import de.eldoria.companies.web.api.Features;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardPartsTest {
    @Test
    void theDashboardIsOffAndPointsAtTheHostedGateway() {
        var settings = new DashboardSettings();

        assertFalse(settings.enabled());
        assertEquals(
                DashboardSettings.DEFAULT_GATEWAY,
                settings.gateway().orElseThrow().toString());
        assertEquals(60, settings.sessionIdleMinutes());
        assertEquals(24, settings.sessionMaxHours());
        assertTrue(settings.feature(Features.ORDERS_BROWSE));
        assertEquals("server-icon.png", settings.branding().icon());
    }

    @Test
    void onlyWebsocketAddressesAreGateways() {
        assertTrue(new DashboardSettings(true, "ws://localhost:8080/api/v1/servers/connect")
                .gateway()
                .isPresent());
        assertTrue(new DashboardSettings(true, "https://companies.eldoria.de")
                .gateway()
                .isEmpty());
        assertTrue(new DashboardSettings(true, "not an address").gateway().isEmpty());
    }

    @Test
    void theBackoffDoublesToAMinuteWithJitterAndStartsOver() {
        var backoff = new Backoff(() -> 0.0);
        assertEquals(Duration.ofSeconds(2), backoff.next());
        assertEquals(Duration.ofSeconds(4), backoff.next());
        assertEquals(Duration.ofSeconds(8), backoff.next());
        assertEquals(Duration.ofSeconds(16), backoff.next());
        assertEquals(Duration.ofSeconds(32), backoff.next());
        assertEquals(Duration.ofSeconds(60), backoff.next());
        assertEquals(Duration.ofSeconds(60), backoff.next());
        backoff.reset();
        assertEquals(Duration.ofSeconds(2), backoff.next());

        var jittered = new Backoff(() -> 0.99);
        assertTrue(jittered.next().compareTo(Duration.ofMillis(1600)) >= 0);
    }

    @Test
    void theKeyCommandIsKeptOutOfTheLog() {
        assertTrue(KeyCommandFilter.hides("Wren issued server command: /companyadmin dashboard key lyd_test"));
        assertTrue(KeyCommandFilter.hides("Wren issued server command: /companies:companyadmin dashboard key x"));
        assertTrue(KeyCommandFilter.hides("Wren issued server command: /CompanyAdmin  Dashboard  KEY x"));
        assertFalse(KeyCommandFilter.hides("Wren issued server command: /companyadmin dashboard status"));
        assertFalse(KeyCommandFilter.hides("Wren issued server command: /company dashboard"));
    }

    @Test
    void actionsArePerPlayerWithinTheWindow() {
        var clock = new MutableClock(Instant.parse("2026-10-24T10:00:00Z"));
        var limit = new ActionLimit(2, Duration.ofSeconds(10), clock);
        var player = UUID.randomUUID();

        assertTrue(limit.take(player));
        assertTrue(limit.take(player));
        assertFalse(limit.take(player));
        assertTrue(limit.take(UUID.randomUUID()));
        clock.advance(Duration.ofSeconds(10));
        assertTrue(limit.take(player));
    }
}
