/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import com.google.common.base.Ticker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FutureCacheTest {
    private final AtomicLong nanos = new AtomicLong();
    private final Ticker ticker = new Ticker() {
        @Override
        public long read() {
            return nanos.get();
        }
    };
    private final AtomicInteger loads = new AtomicInteger();

    private CompletableFuture<Integer> load(String key) {
        return CompletableFuture.completedFuture(loads.incrementAndGet());
    }

    @Test
    void aValueIsLoadedOnceWithinItsLifetime() {
        var cache = new FutureCache<String, Integer>(Duration.ofSeconds(30), 10, ticker);

        assertEquals(1, cache.get("a", this::load).join());
        nanos.addAndGet(Duration.ofSeconds(29).toNanos());
        assertEquals(1, cache.get("a", this::load).join());
        assertEquals(2, cache.get("b", this::load).join());
    }

    @Test
    void anExpiredValueIsLoadedAgain() {
        var cache = new FutureCache<String, Integer>(Duration.ofSeconds(30), 10, ticker);

        cache.get("a", this::load).join();
        nanos.addAndGet(Duration.ofSeconds(31).toNanos());

        assertEquals(2, cache.get("a", this::load).join());
    }

    @Test
    void requestsWhileLoadingShareTheLoad() {
        var cache = new FutureCache<String, Integer>(Duration.ofSeconds(30), 10, ticker);
        var pending = new CompletableFuture<Integer>();

        var first = cache.get("a", key -> pending);
        var second = cache.get("a", this::load);

        assertSame(first, second);
        assertEquals(0, loads.get());
    }

    @Test
    void aFailedLoadIsForgotten() {
        var cache = new FutureCache<String, Integer>(Duration.ofSeconds(30), 10, ticker);

        cache.get("a", key -> CompletableFuture.failedFuture(new IllegalStateException()));

        assertEquals(1, cache.get("a", this::load).join());
    }

    @Test
    void aLifetimeOfZeroCachesNothing() {
        var cache = new FutureCache<String, Integer>(Duration.ZERO, 10, ticker);

        cache.get("a", this::load).join();

        assertEquals(2, cache.get("a", this::load).join());
    }
}
