/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.market;

import de.eldoria.companies.data.wrapper.dashboard.MarketDay;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The change of a material's price: the last week against the week before, weighted by orders.
 */
class MarketChangesTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);

    private static MarketDay day(int daysAgo, String material, int orders, double avg) {
        return new MarketDay(TODAY.minusDays(daysAgo), material, orders, orders * 10L, avg, avg, avg);
    }

    @Test
    void comparesTheLastWeekWithTheWeekBeforeWeightedByOrders() {
        var days = List.of(
                day(13, "STONE", 2, 10.0), day(7, "STONE", 2, 10.0), day(6, "STONE", 1, 9.0), day(0, "STONE", 3, 13.0));

        var changes = MarketHistory.changes(days, TODAY, 3);

        assertEquals(20.0, changes.get("STONE"), 0.0001);
    }

    @Test
    void leavesOutMaterialsWhoseWeeksStandOnTooFewOrders() {
        var days = List.of(
                day(10, "DIRT", 2, 2.0), day(1, "DIRT", 5, 3.0), day(14, "SAND", 9, 1.0), day(2, "SAND", 9, 2.0));

        var changes = MarketHistory.changes(days, TODAY, 3);

        assertFalse(changes.containsKey("DIRT"));
        assertFalse(changes.containsKey("SAND"));
    }
}
