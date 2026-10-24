/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.placeholders;

import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A bounded cache of asynchronously loaded values that never blocks the reader.
 * <p>
 * A missing value starts a load and reads as missing until the load completes. A value older than the refresh
 * interval is still returned, and a reload is started in the background. Only one load per key runs at a time. A load
 * that finds nothing is cached as nothing, so an unknown key is not looked up on every read. A failed load keeps the
 * previous value.
 *
 * @param <K> key
 * @param <V> value
 */
final class StaleCache<K, V> {
    private final Cache<K, Entry<V>> cache;
    private final Map<K, CompletableFuture<Void>> loading = new ConcurrentHashMap<>();
    private final Function<K, CompletableFuture<Optional<V>>> loader;
    private final Supplier<Duration> refresh;
    private final Ticker ticker;

    /**
     * @param loader      loads the value of a key; an empty result means the key has no value
     * @param maximumSize most keys kept at once
     * @param refresh     age after which a value is reloaded on the next read, read on every read
     * @param ticker      source of time
     */
    StaleCache(
            Function<K, CompletableFuture<Optional<V>>> loader,
            long maximumSize,
            Supplier<Duration> refresh,
            Ticker ticker) {
        this.loader = loader;
        this.refresh = refresh;
        this.ticker = ticker;
        this.cache = CacheBuilder.newBuilder().maximumSize(maximumSize).build();
    }

    /**
     * Reads a value, loading it when it is missing or old.
     *
     * @param key key of the value
     * @return the value, or empty when it is not loaded yet or the key has none
     */
    Optional<V> get(K key) {
        var entry = cache.getIfPresent(key);
        if (entry == null) {
            reload(key);
            return Optional.empty();
        }
        if (ticker.read() - entry.loaded() >= refresh.get().toNanos()) reload(key);
        return entry.value();
    }

    /**
     * Reads a value without loading it.
     *
     * @param key key of the value
     * @return the cached value, or empty when there is none
     */
    Optional<V> peek(K key) {
        var entry = cache.getIfPresent(key);
        return entry == null ? Optional.empty() : entry.value();
    }

    /**
     * Whether a key has a cached entry, with or without a value.
     *
     * @param key key
     * @return true when the key was loaded and is still kept
     */
    boolean contains(K key) {
        return cache.getIfPresent(key) != null;
    }

    /**
     * Stores a value as freshly loaded.
     *
     * @param key   key of the value
     * @param value value
     */
    void put(K key, V value) {
        cache.put(key, new Entry<>(Optional.of(value), ticker.read()));
    }

    /**
     * Starts loading a key unless it is loading already. The current value stays readable until the load completes.
     *
     * @param key key to load
     * @return completes when the load is done, whether it succeeded or not
     */
    CompletableFuture<Void> reload(K key) {
        var done = new CompletableFuture<Void>();
        var running = loading.putIfAbsent(key, done);
        if (running != null) return running;
        CompletableFuture<Optional<V>> load;
        try {
            load = loader.apply(key);
        } catch (RuntimeException e) {
            load = CompletableFuture.failedFuture(e);
        }
        load.whenComplete((value, error) -> {
            if (error == null) cache.put(key, new Entry<>(value, ticker.read()));
            loading.remove(key, done);
            done.complete(null);
        });
        return done;
    }

    /**
     * Drops a key.
     *
     * @param key key to drop
     */
    void remove(K key) {
        cache.invalidate(key);
    }

    /**
     * Drops every key whose value matches.
     *
     * @param filter values to drop
     */
    void removeIf(Predicate<V> filter) {
        cache.asMap().values().removeIf(entry -> entry.value().filter(filter).isPresent());
    }

    /**
     * @return the keys kept at the moment
     */
    Set<K> keys() {
        return Set.copyOf(cache.asMap().keySet());
    }

    /**
     * @return number of keys kept at the moment
     */
    long size() {
        cache.cleanUp();
        return cache.size();
    }

    private record Entry<V>(Optional<V> value, long loaded) {}
}
