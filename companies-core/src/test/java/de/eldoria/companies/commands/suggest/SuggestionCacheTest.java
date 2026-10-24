/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuggestionCacheTest {
    private static final Duration WAIT = Duration.ofMillis(100);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private static SuggestionsBuilder builder(String typed) {
        return new SuggestionsBuilder("kick " + typed, 5);
    }

    private static List<String> texts(com.mojang.brigadier.suggestion.Suggestions suggestions) {
        return suggestions.getList().stream().map(Suggestion::getText).toList();
    }

    private static void fill(List<String> names, SuggestionsBuilder builder) {
        CompanyProviders.matching(names, builder.getRemaining()).forEach(builder::suggest);
    }

    @Test
    void cachedValuesAreSuggestedAtOnce() throws Exception {
        var loads = new AtomicInteger();
        var cache = new SuggestionCache<String, List<String>>(
                Duration.ofMinutes(1),
                executor,
                key -> {
                    loads.incrementAndGet();
                    return List.of("Alex", "Steve", "alice");
                },
                WAIT);
        cache.load("company").get(1, TimeUnit.SECONDS);

        var future = cache.suggest("company", builder("al"), SuggestionCacheTest::fill);

        assertTrue(future.isDone());
        assertEquals(
                List.of("Alex", "alice"), texts(future.get()).stream().sorted().toList());
        assertEquals(1, loads.get());
    }

    @Test
    void aFastLoadIsSuggestedOnTheFirstKeystroke() throws Exception {
        var cache = new SuggestionCache<String, List<String>>(
                Duration.ofMinutes(1), executor, key -> List.of("Steve"), Duration.ofSeconds(5));

        var suggestions = cache.suggest("company", builder("st"), SuggestionCacheTest::fill)
                .get(5, TimeUnit.SECONDS);

        assertEquals(List.of("Steve"), texts(suggestions));
    }

    @Test
    void aSlowLoadSuggestsNothingAndIsCachedForTheNextKeystroke() throws Exception {
        var release = new CountDownLatch(1);
        var cache = new SuggestionCache<String, List<String>>(
                Duration.ofMinutes(1),
                executor,
                key -> {
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return List.of("Steve");
                },
                WAIT);

        var first = cache.suggest("company", builder("st"), SuggestionCacheTest::fill)
                .get(1, TimeUnit.SECONDS);
        assertTrue(first.isEmpty());

        release.countDown();
        cache.load("company").get(1, TimeUnit.SECONDS);
        var second = cache.suggest("company", builder("st"), SuggestionCacheTest::fill);
        assertTrue(second.isDone());
        assertEquals(List.of("Steve"), texts(second.get()));
    }

    @Test
    void concurrentMissesLoadOnce() throws Exception {
        var release = new CountDownLatch(1);
        var loads = new AtomicInteger();
        var cache = new SuggestionCache<String, List<String>>(
                Duration.ofMinutes(1),
                executor,
                key -> {
                    loads.incrementAndGet();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return List.of("Steve");
                },
                WAIT);

        var first = cache.load("company");
        var second = cache.load("company");
        release.countDown();

        assertEquals(first.get(1, TimeUnit.SECONDS), second.get(1, TimeUnit.SECONDS));
        assertEquals(1, loads.get());
    }

    @Test
    void aFailingLoadSuggestsNothing() throws Exception {
        var cache = new SuggestionCache<String, List<String>>(
                Duration.ofMinutes(1),
                executor,
                key -> {
                    throw new IllegalStateException("database down");
                },
                WAIT);

        var suggestions = cache.suggest("company", builder("st"), SuggestionCacheTest::fill)
                .get(1, TimeUnit.SECONDS);

        assertTrue(suggestions.isEmpty());
        assertTrue(cache.cached("company").isEmpty());
    }

    @Test
    void aRejectingExecutorSuggestsNothing() throws Exception {
        executor.shutdown();
        var cache = new SuggestionCache<String, List<String>>(
                Duration.ofMinutes(1), executor, key -> List.of("Steve"), WAIT);

        var suggestions = cache.suggest("company", builder("st"), SuggestionCacheTest::fill)
                .get(1, TimeUnit.SECONDS);

        assertTrue(suggestions.isEmpty());
    }
}
