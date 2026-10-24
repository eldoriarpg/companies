/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
                clock,
                Duration.ofSeconds(2),
                Runnable::run);
        assertTrue(health.up());
        database.set(false);
        assertTrue(health.up());
        assertEquals(1, checks.get());
        clock.advance(Duration.ofSeconds(6));
        assertFalse(health.up());
        assertEquals(2, checks.get());
    }

    @Test
    void aHangingDatabaseCountsAsDownWithoutHoldingRequests() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-24T12:00:00Z"));
        CountDownLatch hang = new CountDownLatch(1);
        AtomicInteger checks = new AtomicInteger();
        Health health = new Health(
                () -> {
                    checks.incrementAndGet();
                    try {
                        return hang.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                },
                clock,
                Duration.ofMillis(200),
                Executors.newVirtualThreadPerTaskExecutor());
        long start = System.nanoTime();
        assertFalse(health.up());
        clock.advance(Duration.ofSeconds(6));
        assertFalse(health.up());
        assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 2);
        assertEquals(1, checks.get());
        hang.countDown();
        clock.advance(Duration.ofSeconds(6));
        assertTrue(health.up());
    }
}
