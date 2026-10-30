/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import de.eldoria.companies.components.event.EventKind;
import de.eldoria.companies.components.event.EventState;
import de.eldoria.companies.dashboard.MutableClock;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;

import static de.eldoria.companies.services.events.TestEvents.START;
import static de.eldoria.companies.services.events.TestEvents.event;
import static de.eldoria.companies.services.events.TestEvents.withState;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ActiveEventsTest {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-30T12:00:00Z"));
    private final List<ServerEvent> stored = new ArrayList<>();
    private final AtomicInteger reads = new AtomicInteger();
    private final ActiveEvents events = new ActiveEvents(
            () -> {
                reads.incrementAndGet();
                return List.copyOf(stored);
            },
            clock,
            Duration.ofSeconds(30));

    @Test
    void theEventsAreReadOnceWithinTheLifetimeAndAgainAfterIt() {
        stored.add(event(1, EventKind.TAX_REDUCTION, 5));

        assertEquals(1, events.live().size());
        stored.add(event(2, EventKind.TAX_REDUCTION, 5));
        assertEquals(1, events.live().size());
        assertEquals(1, reads.get());

        clock.advance(Duration.ofSeconds(30));
        assertEquals(2, events.live().size());
        assertEquals(2, reads.get());
    }

    @Test
    void aSignalledChangeIsReadAtOnce() {
        stored.add(event(1, EventKind.TAX_REDUCTION, 5));
        events.live();
        stored.clear();

        events.invalidate();

        assertEquals(List.of(), events.live());
        assertEquals(2, reads.get());
    }

    @Test
    void aChangeSignalledWhileTheEventsAreReadIsNotLostForTheLifetime() {
        var changing = new ArrayList<ServerEvent>();
        var calls = new AtomicInteger();
        var cache = new ActiveEvents[1];
        cache[0] = new ActiveEvents(
                () -> {
                    var read = List.copyOf(changing);
                    if (calls.incrementAndGet() == 1) {
                        changing.add(event(1, EventKind.TAX_REDUCTION, 5));
                        cache[0].invalidate();
                    }
                    return read;
                },
                clock,
                Duration.ofSeconds(30));

        assertEquals(List.of(), cache[0].live());
        assertEquals(List.of(1), ids(cache[0].live()));
        assertEquals(2, calls.get());
        assertEquals(List.of(1), ids(cache[0].live()));
        assertEquals(2, calls.get());
    }

    @Test
    void anEventAppliesOnlyWhileActiveAndWithinItsTime() {
        stored.add(event(1, EventKind.TAX_REDUCTION, 5));
        stored.add(withState(event(2, EventKind.TAX_REDUCTION, 5), EventState.SCHEDULED));
        stored.add(event(3, EventKind.TAX_REDUCTION, 5, START.minusDays(2), List.of(), List.of()));

        assertEquals(List.of(1), ids(events.activeAt(START.plusHours(1))));
        assertEquals(List.of(), ids(events.activeAt(START.minusMinutes(1))));
        assertEquals(List.of(2), ids(events.upcoming(START.plusHours(1))));
    }

    @Test
    void matchingKeepsTheEventsWhoseScopeFitsTheOrder() {
        stored.add(event(1, EventKind.TAX_REDUCTION, 5));
        stored.add(event(2, EventKind.REWARD_MULTIPLIER, 2, START, List.of("WHEAT"), List.of()));
        stored.add(event(3, EventKind.TAX_REDUCTION, 5, START, List.of(), List.of(2)));
        var at = START.plusHours(1);

        assertEquals(List.of(1, 2, 3), ids(events.matching(at, List.of("WHEAT"), OptionalInt.of(2))));
        assertEquals(List.of(1), ids(events.matching(at, List.of("STONE"), OptionalInt.of(1))));
        assertEquals(List.of(1, 2), ids(events.matching(at, List.of("WHEAT"), OptionalInt.empty())));
    }

    private static List<Integer> ids(List<ServerEvent> events) {
        return events.stream().map(ServerEvent::id).toList();
    }
}
