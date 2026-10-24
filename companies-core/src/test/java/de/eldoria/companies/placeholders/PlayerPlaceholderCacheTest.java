/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.placeholders;

import com.google.common.base.Ticker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPlaceholderCacheTest {
    private final AtomicLong nanos = new AtomicLong();
    private final Ticker ticker = new Ticker() {
        @Override
        public long read() {
            return nanos.get();
        }
    };
    private final AtomicInteger loads = new AtomicInteger();
    private final Map<UUID, CompletableFuture<Optional<PlayerView>>> pending = new HashMap<>();

    private CompletableFuture<Optional<PlayerView>> load(UUID player) {
        var count = loads.incrementAndGet();
        var waiting = pending.remove(player);
        if (waiting != null) return waiting;
        return CompletableFuture.completedFuture(Optional.of(view(player, count)));
    }

    private static PlayerView view(UUID player, int openOrders) {
        return new PlayerView(player, OptionalInt.of(7), Optional.of(Role.MEMBER), openOrders, 7, 0);
    }

    private PlayerPlaceholderCache cache(int size) {
        return new PlayerPlaceholderCache(this::load, size, () -> Duration.ofMinutes(5), ticker);
    }

    @Test
    void aMissStartsALoadAndAnswersNothing() {
        var cache = cache(10);
        var player = UUID.randomUUID();
        var load = new CompletableFuture<Optional<PlayerView>>();
        pending.put(player, load);

        assertEquals(Optional.empty(), cache.get(player));
        assertEquals(Optional.empty(), cache.get(player));
        assertEquals(1, loads.get());

        load.complete(Optional.of(view(player, 3)));
        assertEquals(3, cache.get(player).orElseThrow().openOrders());
    }

    @Test
    void offlinePlayersAreBounded() {
        var cache = cache(3);
        for (var i = 0; i < 20; i++) cache.get(UUID.randomUUID());

        assertTrue(cache.offlineSize() <= 3);
    }

    @Test
    void onlinePlayersAreNotEvicted() {
        var cache = cache(1);
        var online = UUID.randomUUID();
        cache.join(online).join();
        for (var i = 0; i < 20; i++) cache.get(UUID.randomUUID());

        assertEquals(1, cache.get(online).orElseThrow().openOrders());
    }

    @Test
    void anOldValueIsAnsweredWhileItReloads() {
        var cache = cache(10);
        var player = UUID.randomUUID();
        cache.get(player);
        nanos.addAndGet(Duration.ofMinutes(6).toNanos());
        var reload = new CompletableFuture<Optional<PlayerView>>();
        pending.put(player, reload);

        assertEquals(1, cache.get(player).orElseThrow().openOrders());
        reload.complete(Optional.of(view(player, 9)));
        assertEquals(9, cache.get(player).orElseThrow().openOrders());
    }

    @Test
    void aFreshValueIsNotReloaded() {
        var cache = cache(10);
        var player = UUID.randomUUID();
        cache.get(player);
        nanos.addAndGet(Duration.ofMinutes(4).toNanos());

        cache.get(player);

        assertEquals(1, loads.get());
    }

    @Test
    void aPlayerLeavingKeepsTheValueOffline() {
        var cache = cache(10);
        var player = UUID.randomUUID();
        cache.join(player).join();

        cache.quit(player);

        assertEquals(1, cache.get(player).orElseThrow().openOrders());
        assertEquals(1, loads.get());
    }

    @Test
    void refreshingReloadsOnlyOnlinePlayers() {
        var cache = cache(10);
        var online = UUID.randomUUID();
        cache.join(online).join();
        cache.get(UUID.randomUUID());

        cache.refreshOnline().join();

        assertEquals(3, loads.get());
        assertEquals(3, cache.get(online).orElseThrow().openOrders());
    }

    @Test
    void invalidatingReloadsCachedPlayersOnly() {
        var cache = cache(10);
        var known = UUID.randomUUID();
        cache.get(known);

        cache.invalidate(known).join();
        cache.invalidate(UUID.randomUUID()).join();

        assertEquals(2, loads.get());
        assertEquals(2, cache.get(known).orElseThrow().openOrders());
    }

    @Test
    void disbandingReloadsTheMembers() {
        var cache = cache(10);
        var online = UUID.randomUUID();
        var offline = UUID.randomUUID();
        cache.join(online).join();
        cache.get(offline);

        cache.invalidateCompany(7).join();
        cache.invalidateCompany(8).join();

        assertEquals(4, loads.get());
    }

    @Test
    void aFailedLoadKeepsThePreviousValue() {
        var cache = cache(10);
        var player = UUID.randomUUID();
        cache.get(player);
        pending.put(player, CompletableFuture.failedFuture(new IllegalStateException()));

        cache.invalidate(player).join();

        assertEquals(1, cache.get(player).orElseThrow().openOrders());
    }
}
