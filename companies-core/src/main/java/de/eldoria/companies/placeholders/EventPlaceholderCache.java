/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.placeholders;

import de.eldoria.companies.data.wrapper.event.ServerEvent;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The server events that run, as the placeholders read them: the soonest start first. Replaced as a whole by
 * {@link #refresh()}, on the periodic refresh and when an event changed; until the first refresh it reads as not
 * loaded. An event whose end passed reads as not running at once.
 */
public final class EventPlaceholderCache {
    private final Supplier<CompletableFuture<List<ServerEvent>>> loader;
    private final Clock clock;
    private volatile List<ServerEvent> live;

    /**
     * @param loader loads the events that wait or run, the soonest start first
     * @param clock  clock giving the current time in UTC
     */
    public EventPlaceholderCache(Supplier<CompletableFuture<List<ServerEvent>>> loader, Clock clock) {
        this.loader = loader;
        this.clock = clock;
    }

    /**
     * Reloads the events. A failed load keeps the events loaded before.
     *
     * @return completes when they are loaded
     */
    public CompletableFuture<Void> refresh() {
        return loader.get().thenAccept(found -> live = List.copyOf(found)).exceptionally(error -> null);
    }

    /**
     * @return whether the events were loaded at least once
     */
    public boolean loaded() {
        return live != null;
    }

    private List<ServerEvent> active() {
        var current = live;
        if (current == null) return List.of();
        var now = LocalDateTime.now(clock);
        return current.stream().filter(event -> event.activeAt(now)).toList();
    }

    /**
     * @return the number of events that run
     */
    public int count() {
        return active().size();
    }

    /**
     * @param place the place in the list, from one
     * @return the running event at the place, the soonest start first; empty beyond the list
     */
    public Optional<ServerEvent> at(int place) {
        var active = active();
        if (place < 1 || place > active.size()) return Optional.empty();
        return Optional.of(active.get(place - 1));
    }
}
