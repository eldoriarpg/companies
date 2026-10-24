/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {
    @Test
    void limitsEachKeyInItsWindowAndForgetsQuietKeys() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-24T12:00:00Z"));
        RateLimiter limiter = new RateLimiter(2, Duration.ofMinutes(1), clock);
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"));
        assertEquals(2, limiter.keys());

        clock.advance(Duration.ofSeconds(61));
        limiter.sweep();
        assertEquals(0, limiter.keys());
        assertTrue(limiter.tryAcquire("a"));
    }
}
