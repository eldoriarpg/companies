/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import de.eldoria.companies.data.repository.AServerEventData;
import de.eldoria.companies.data.wrapper.event.EventState;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * The server events of the network as this node sees them, read through a short cache.
 * <p>
 * Every node keeps the events that wait or run for {@link #LIFETIME} at most. A change signalled through the
 * {@link de.eldoria.companies.nodes.EventNetwork} drops them at once, so they are read again on the next call; a lost
 * signal costs the lifetime, never an event. Whether an event applies is decided by the time asked for, so an event
 * whose end passed stops applying on every node at its end, even before the primary ended it, and one stops applying
 * on a node within the lifetime when an admin ended it and the signal was lost. Reads block while the cache is read
 * again; call them off the main thread.
 */
public final class ActiveEvents {
    /**
     * How long the events are kept before they are read again.
     */
    public static final Duration LIFETIME = Duration.ofSeconds(30);

    private final Supplier<List<ServerEvent>> source;
    private final Clock clock;
    private final Duration lifetime;
    private final AtomicLong generation = new AtomicLong();
    private volatile @Nullable Snapshot snapshot;

    /**
     * @param events the event repository
     * @param clock  clock deciding when the cache expires
     */
    public ActiveEvents(AServerEventData events, Clock clock) {
        this(events::live, clock, LIFETIME);
    }

    /**
     * @param source   reads the events that wait or run
     * @param clock    clock deciding when the cache expires
     * @param lifetime how long the events are kept
     */
    public ActiveEvents(Supplier<List<ServerEvent>> source, Clock clock, Duration lifetime) {
        this.source = source;
        this.clock = clock;
        this.lifetime = lifetime;
    }

    /**
     * @return the events that wait or run, the soonest start first
     */
    public List<ServerEvent> live() {
        var current = snapshot;
        var now = clock.instant();
        if (current != null && current.valid(generation.get(), now)) return current.events();
        return load(now);
    }

    /**
     * @param at a time, in UTC
     * @return the events that apply at the time, the soonest start first
     */
    public List<ServerEvent> activeAt(LocalDateTime at) {
        return live().stream().filter(event -> event.activeAt(at)).toList();
    }

    /**
     * The events that apply to an order at a time: active then, and with a scope matching the order, see
     * {@link ServerEvent#matches}.
     *
     * @param at        the time, in UTC, such as when the order was completed
     * @param materials the materials of the order's lines
     * @param level     the level of the company, empty for an order worked on alone
     * @return the events, the soonest start first
     */
    public List<ServerEvent> matching(LocalDateTime at, Collection<String> materials, OptionalInt level) {
        return live().stream()
                .filter(event -> event.activeAt(at) && event.matches(materials, level))
                .toList();
    }

    /**
     * @param at a time, in UTC
     * @return the events that still wait for their start and end after the time, the soonest start first
     */
    public List<ServerEvent> upcoming(LocalDateTime at) {
        return live().stream()
                .filter(event ->
                        event.state() == EventState.SCHEDULED && event.ends().isAfter(at))
                .toList();
    }

    /**
     * Drops the cached events, so the next call reads them again.
     */
    public void invalidate() {
        generation.incrementAndGet();
    }

    private synchronized List<ServerEvent> load(Instant now) {
        var current = snapshot;
        var expected = generation.get();
        if (current != null && current.valid(expected, now)) return current.events();
        var events = List.copyOf(source.get());
        snapshot = new Snapshot(events, now.plus(lifetime), expected);
        return events;
    }

    private record Snapshot(List<ServerEvent> events, Instant expires, long generation) {
        boolean valid(long current, Instant now) {
            return generation == current && now.isBefore(expires);
        }
    }
}
