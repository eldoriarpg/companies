/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import de.eldoria.companies.components.event.EventKind;
import de.eldoria.companies.services.events.EventEffects.Line;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.eldoria.companies.services.events.TestEvents.START;
import static de.eldoria.companies.services.events.TestEvents.event;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventEffectsTest {
    private static final double DELTA = 1e-9;

    @Test
    void taxReductionsAddUpAndTheTaxNeverDropsBelowZero() {
        var events = List.of(
                event(1, EventKind.TAX_REDUCTION, 3),
                event(2, EventKind.TAX_REDUCTION, 4),
                event(3, EventKind.FEE_REDUCTION, 50),
                event(4, EventKind.REWARD_MULTIPLIER, 2));

        assertEquals(3.0, EventEffects.taxPercent(10, events), DELTA);
        assertEquals(0.0, EventEffects.taxPercent(5, events), DELTA);
        assertEquals(10.0, EventEffects.taxPercent(10, List.of()), DELTA);
    }

    @Test
    void feeReductionsAddUpInPercentAndTheFeeNeverDropsBelowZero() {
        var half = List.of(event(1, EventKind.FEE_REDUCTION, 30), event(2, EventKind.FEE_REDUCTION, 20));
        var more = List.of(event(1, EventKind.FEE_REDUCTION, 70), event(2, EventKind.FEE_REDUCTION, 60));

        assertEquals(50.0, EventEffects.fee(100, half), DELTA);
        assertEquals(0.0, EventEffects.fee(100, more), DELTA);
        assertEquals(100.0, EventEffects.fee(100, List.of(event(3, EventKind.TAX_REDUCTION, 50))), DELTA);
    }

    @Test
    void multipliersMultiplyUpToTheCap() {
        var events = List.of(
                event(1, EventKind.REWARD_MULTIPLIER, 1.5),
                event(2, EventKind.REWARD_MULTIPLIER, 1.5),
                event(3, EventKind.TAX_REDUCTION, 5));

        assertEquals(2.25, EventEffects.multiplier(events, 3), DELTA);
        assertEquals(2.0, EventEffects.multiplier(events, 2), DELTA);
        assertEquals(1.0, EventEffects.multiplier(List.of(), 3), DELTA);
        assertEquals(1.0, EventEffects.multiplier(events, 0.5), DELTA);
    }

    @Test
    void theExtraIsSplitInTheOrderTheEventsStartedAndStopsAtTheCap() {
        var first = event(7, EventKind.REWARD_MULTIPLIER, 2, START, List.of(), List.of());
        var second = event(3, EventKind.REWARD_MULTIPLIER, 2, START.plusHours(1), List.of(), List.of());
        var third = event(5, EventKind.REWARD_MULTIPLIER, 2, START.plusHours(2), List.of(), List.of());

        var extra = EventEffects.extra(List.of(new Line("WHEAT", 100)), List.of(third, second, first), 3);

        assertEquals(200.0, extra.total(), DELTA);
        assertEquals(Map.of(7, 100.0, 3, 100.0), extra.byEvent());
        assertEquals(List.of(7, 3), List.copyOf(extra.byEvent().keySet()));
    }

    @Test
    void anItemScopedMultiplierPaysOnItsLinesOnly() {
        var wheat = event(1, EventKind.REWARD_MULTIPLIER, 1.5, START, List.of("WHEAT"), List.of());
        var all = event(2, EventKind.REWARD_MULTIPLIER, 2, START.plusHours(1), List.of(), List.of());
        var lines = List.of(new Line("WHEAT", 100), new Line("CARROT", 40));

        var extra = EventEffects.extra(lines, List.of(wheat, all), 2.5);

        assertEquals(150.0 + 40.0, extra.total(), DELTA);
        assertEquals(50.0, extra.byEvent().get(1), DELTA);
        assertEquals(100.0 + 40.0, extra.byEvent().get(2), DELTA);
    }

    @Test
    void twoItemScopedMultipliersOnDifferentLinesAndAServerWideOneStayUnderTheCap() {
        var wheat = event(1, EventKind.REWARD_MULTIPLIER, 1.5, START, List.of("WHEAT"), List.of());
        var carrot = event(2, EventKind.REWARD_MULTIPLIER, 1.2, START.plusHours(1), List.of("CARROT"), List.of());
        var all = event(3, EventKind.REWARD_MULTIPLIER, 1.5, START.plusHours(2), List.of(), List.of());
        var lines = List.of(new Line("WHEAT", 100), new Line("CARROT", 100), new Line("STONE", 100));

        var extra = EventEffects.extra(lines, List.of(all, carrot, wheat), 3);

        assertEquals(50.0, extra.byEvent().get(1), DELTA);
        assertEquals(20.0, extra.byEvent().get(2), DELTA);
        assertEquals(75.0 + 60.0 + 50.0, extra.byEvent().get(3), DELTA);
        assertEquals(50.0 + 20.0 + 185.0, extra.total(), DELTA);
        assertTrue(extra.exhausted().isEmpty());
    }

    @Test
    void anEventThatPaysLessThanItsPartLeavesTheLaterOnesTheFactorItReallyPaid() {
        var first = event(1, EventKind.REWARD_MULTIPLIER, 2, START, List.of(), List.of());
        var second = event(2, EventKind.REWARD_MULTIPLIER, 2, START.plusHours(1), List.of(), List.of());
        var lines = List.of(new Line("WHEAT", 60), new Line("CARROT", 40));

        var extra = EventEffects.extra(lines, List.of(first, second), 10, event -> event.id() == 1 ? 50.0 : 1000.0);

        assertEquals(50.0, extra.byEvent().get(1), DELTA);
        assertEquals(150.0, extra.byEvent().get(2), DELTA);
        assertEquals(200.0, extra.total(), DELTA);
        assertEquals(Set.of(1), extra.exhausted());
    }

    @Test
    void anEventWithNothingLeftPaysNothingAndIsExhausted() {
        var first = event(1, EventKind.REWARD_MULTIPLIER, 2, START, List.of(), List.of());
        var second = event(2, EventKind.REWARD_MULTIPLIER, 1.5, START.plusHours(1), List.of(), List.of());

        var extra = EventEffects.extra(
                List.of(new Line("WHEAT", 100)), List.of(first, second), 3, event -> event.id() == 1 ? 0.0 : 1000.0);

        assertEquals(Map.of(2, 50.0), extra.byEvent());
        assertEquals(Set.of(1), extra.exhausted());
    }

    @Test
    void anEventThatPaysLessThanItsPartNeverPaysMoreThanItCan() {
        var event = event(1, EventKind.REWARD_MULTIPLIER, 1.7, START, List.of(), List.of());
        var lines =
                List.of(new Line("WHEAT", 0.7), new Line("CARROT", 0.2), new Line("STONE", 0.1), new Line("SAND", 3.3));
        for (var step = 1; step < 1000; step++) {
            var limit = step * 0.0021;

            var extra = EventEffects.extra(lines, List.of(event), 3, ignored -> limit);

            assertTrue(extra.total() <= limit, "paid " + extra.total() + " of " + limit);
            assertTrue(extra.byEvent().getOrDefault(1, 0.0) <= limit);
        }
    }

    @Test
    void noMultiplierPaysNoExtra() {
        var extra =
                EventEffects.extra(List.of(new Line("WHEAT", 100)), List.of(event(1, EventKind.TAX_REDUCTION, 5)), 3);

        assertEquals(0.0, extra.total(), DELTA);
        assertTrue(extra.byEvent().isEmpty());
    }
}
