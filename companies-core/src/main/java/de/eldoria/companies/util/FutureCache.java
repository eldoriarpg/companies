/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Keeps the results of asynchronous loads for a fixed time.
 * <p>
 * The future itself is cached, so requests arriving while a value loads share the load. A load that fails is
 * forgotten, and the next request loads again. A lifetime of zero caches nothing.
 *
 * @param <K> key of a value
 * @param <V> loaded value
 */
public final class FutureCache<K, V> {
    private final Cache<K, CompletableFuture<V>> cache;
    private final boolean enabled;

    /**
     * @param lifetime how long a loaded value is kept
     * @param maximumSize most values kept at once
     */
    public FutureCache(Duration lifetime, long maximumSize) {
        this(lifetime, maximumSize, Ticker.systemTicker());
    }

    /**
     * @param lifetime    how long a loaded value is kept
     * @param maximumSize most values kept at once
     * @param ticker      source of time
     */
    public FutureCache(Duration lifetime, long maximumSize, Ticker ticker) {
        this.enabled = !lifetime.isZero() && !lifetime.isNegative();
        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(enabled ? lifetime : Duration.ofNanos(1))
                .maximumSize(maximumSize)
                .ticker(ticker)
                .build();
    }

    /**
     * The value of a key, loaded unless it is cached.
     *
     * @param key    key of the value
     * @param loader loads the value of the key
     * @return the cached or a new future of the value
     */
    public CompletableFuture<V> get(K key, Function<K, CompletableFuture<V>> loader) {
        if (!enabled) return loader.apply(key);
        var future = cache.asMap().computeIfAbsent(key, loader);
        future.whenComplete((value, error) -> {
            if (error != null) cache.asMap().remove(key, future);
        });
        return future;
    }

    /**
     * Forgets every cached value.
     */
    public void clear() {
        cache.invalidateAll();
    }
}
