/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Short-lived cache of data that suggestions read from the database.
 * <p>
 * Suggestions never block the server thread. A cached value is suggested at once. On a miss the value is loaded on
 * the executor and suggested when it arrives within {@link #WAIT}; otherwise the suggestions stay empty and the next
 * keystroke finds the value in the cache.
 *
 * @param <K> key, such as the player
 * @param <V> cached value
 */
public final class SuggestionCache<K, V> {
    /**
     * How long a suggestion waits for a value that is not cached yet.
     */
    public static final Duration WAIT = Duration.ofMillis(300);

    private final Cache<K, V> cache;
    private final Map<K, CompletableFuture<V>> loading = new ConcurrentHashMap<>();
    private final Executor executor;
    private final Function<K, V> loader;
    private final Duration wait;

    /**
     * @param lifetime how long a value stays cached
     * @param executor executor loading missing values
     * @param loader   loads the value of a key, may block
     */
    public SuggestionCache(Duration lifetime, Executor executor, Function<K, V> loader) {
        this(lifetime, executor, loader, WAIT);
    }

    /**
     * @param lifetime how long a value stays cached
     * @param executor executor loading missing values
     * @param loader   loads the value of a key, may block
     * @param wait     how long a suggestion waits for a missing value
     */
    public SuggestionCache(Duration lifetime, Executor executor, Function<K, V> loader, Duration wait) {
        this.cache = CacheBuilder.newBuilder().expireAfterWrite(lifetime).build();
        this.executor = executor;
        this.loader = loader;
        this.wait = wait;
    }

    /**
     * @param key key of the value
     * @return the cached value, without loading it
     */
    public Optional<V> cached(K key) {
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    /**
     * Fills suggestions from the value of a key.
     *
     * @param key     key of the value
     * @param builder builder of the suggestions
     * @param fill    adds the suggestions for a value
     * @return the suggestions; empty when the value is not loaded within the wait
     */
    public CompletableFuture<Suggestions> suggest(
            K key, SuggestionsBuilder builder, BiConsumer<V, SuggestionsBuilder> fill) {
        var hit = cache.getIfPresent(key);
        if (hit != null) {
            fill.accept(hit, builder);
            return builder.buildFuture();
        }
        var empty = builder.build();
        var late = builder.createOffset(builder.getStart());
        return load(key)
                .thenApply(value -> {
                    fill.accept(value, late);
                    return late.build();
                })
                .exceptionally(err -> empty)
                .completeOnTimeout(empty, wait.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Drops the value of a key, so the next suggestion loads it again.
     *
     * @param key key of the value
     */
    public void invalidate(K key) {
        cache.invalidate(key);
    }

    /**
     * Starts loading the value of a key, unless it is loading already.
     *
     * @param key key of the value
     * @return the value once loaded
     */
    public CompletableFuture<V> load(K key) {
        var pending = new CompletableFuture<V>();
        var running = loading.putIfAbsent(key, pending);
        if (running != null) return running;
        try {
            executor.execute(() -> {
                try {
                    var value = loader.apply(key);
                    cache.put(key, value);
                    pending.complete(value);
                } catch (RuntimeException e) {
                    pending.completeExceptionally(e);
                } finally {
                    loading.remove(key, pending);
                }
            });
        } catch (RejectedExecutionException e) {
            loading.remove(key, pending);
            pending.completeExceptionally(e);
        }
        return pending;
    }
}
