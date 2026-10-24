/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthTest {
    @Test
    void reusesACheckForAFewSeconds() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-24T12:00:00Z"));
        AtomicInteger checks = new AtomicInteger();
        AtomicBoolean database = new AtomicBoolean(true);
        Health health = new Health(
                () -> {
                    checks.incrementAndGet();
                    return database.get();
                },
                clock);
        assertTrue(health.up());
        database.set(false);
        assertTrue(health.up());
        assertEquals(1, checks.get());
        clock.advance(Duration.ofSeconds(6));
        assertFalse(health.up());
        assertEquals(2, checks.get());
    }
}
