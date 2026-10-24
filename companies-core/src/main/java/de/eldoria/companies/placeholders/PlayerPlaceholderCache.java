/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.placeholders;

import com.google.common.base.Ticker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The placeholder values of players.
 * <p>
 * Online players are kept while they are online and reloaded by {@link #refreshOnline()}. Offline players, for
 * example on a leaderboard sign or in a tab list of another server, are kept in a cache of bounded size; a value older
 * than the refresh interval is still answered while it reloads. The first read of an unknown player starts a load and
 * answers nothing.
 */
public final class PlayerPlaceholderCache {
    private final Set<UUID> present = ConcurrentHashMap.newKeySet();
    private final StaleCache<UUID, PlayerView> online;
    private final StaleCache<UUID, PlayerView> offline;

    /**
     * @param loader           loads the values of a player
     * @param offlineCacheSize most offline players kept
     * @param refresh          age after which a value is reloaded, read on every read
     */
    public PlayerPlaceholderCache(
            Function<UUID, CompletableFuture<Optional<PlayerView>>> loader,
            int offlineCacheSize,
            Supplier<Duration> refresh) {
        this(loader, offlineCacheSize, refresh, Ticker.systemTicker());
    }

    /**
     * @param loader           loads the values of a player
     * @param offlineCacheSize most offline players kept
     * @param refresh          age after which a value is reloaded, read on every read
     * @param ticker           source of time
     */
    public PlayerPlaceholderCache(
            Function<UUID, CompletableFuture<Optional<PlayerView>>> loader,
            int offlineCacheSize,
            Supplier<Duration> refresh,
            Ticker ticker) {
        this.online = new StaleCache<>(loader, Long.MAX_VALUE, refresh, ticker);
        this.offline = new StaleCache<>(loader, offlineCacheSize, refresh, ticker);
    }

    /**
     * The values of a player.
     *
     * @param player id of the player
     * @return the values, or empty while they are loaded for the first time
     */
    public Optional<PlayerView> get(UUID player) {
        if (present.contains(player)) return online.get(player);
        return offline.get(player);
    }

    /**
     * Keeps a player who joined and reloads the values. Values cached while the player was offline are answered
     * until the reload completes.
     *
     * @param player id of the player
     * @return completes when the values are loaded
     */
    public CompletableFuture<Void> join(UUID player) {
        offline.peek(player).ifPresent(view -> online.put(player, view));
        offline.remove(player);
        present.add(player);
        return online.reload(player);
    }

    /**
     * Moves a player who left to the offline cache.
     *
     * @param player id of the player
     */
    public void quit(UUID player) {
        present.remove(player);
        online.peek(player).ifPresent(view -> offline.put(player, view));
        online.remove(player);
    }

    /**
     * Reloads every online player.
     *
     * @return completes when every reload is done
     */
    public CompletableFuture<Void> refreshOnline() {
        return CompletableFuture.allOf(present.stream().map(online::reload).toArray(CompletableFuture[]::new));
    }

    /**
     * Reloads a player after a change. An offline player not cached stays unloaded.
     *
     * @param player id of the player
     * @return completes when the reload is done
     */
    public CompletableFuture<Void> invalidate(UUID player) {
        if (present.contains(player)) return online.reload(player);
        if (offline.contains(player)) return offline.reload(player);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Reloads every cached member of a company, for example after it was disbanded.
     *
     * @param company id of the company
     * @return completes when every reload is done
     */
    public CompletableFuture<Void> invalidateCompany(int company) {
        var members = new ArrayList<CompletableFuture<Void>>();
        for (var player : present) {
            if (isMember(online.peek(player), company)) members.add(online.reload(player));
        }
        for (var player : offline.keys()) {
            if (isMember(offline.peek(player), company)) members.add(offline.reload(player));
        }
        return CompletableFuture.allOf(members.toArray(CompletableFuture[]::new));
    }

    /**
     * @return number of offline players kept
     */
    public long offlineSize() {
        return offline.size();
    }

    private static boolean isMember(Optional<PlayerView> view, int company) {
        return view.map(value -> value.company().isPresent() && value.company().getAsInt() == company)
                .orElse(false);
    }
}
