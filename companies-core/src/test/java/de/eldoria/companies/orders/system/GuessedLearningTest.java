/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import de.eldoria.companies.configuration.elements.systemorders.LearningSettings;
import de.eldoria.companies.orders.system.GuessedLearning.Ended;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static de.eldoria.companies.orders.system.GuessedLearning.EXPIRED;
import static de.eldoria.companies.orders.system.GuessedLearning.TAKEN;
import static de.eldoria.companies.orders.system.GuessedLearning.WITHDRAWN;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GuessedLearningTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 1, 8, 0);

    private final LearningSettings settings = new LearningSettings();

    @Test
    void outcomesAreCountedPerMaterial() {
        var stats = GuessedLearning.stats(
                        List.of(
                                ended("DIRT", 0.8, 0, TAKEN, 20),
                                ended("DIRT", 0.8, 1, TAKEN, 60),
                                ended("DIRT", 0.85, 2, EXPIRED, 120),
                                ended("DIRT", 0.85, 3, WITHDRAWN, 10),
                                ended("DIRT", 0.85, 4, null, 0),
                                ended("SAND", 0.8, 0, null, 0)),
                        settings)
                .get("DIRT");

        assertEquals(5, stats.posted());
        assertEquals(2, stats.taken());
        assertEquals(1, stats.expired());
        assertEquals(40.0, stats.minutesToTake());
        assertArrayEquals(new int[] {2, 2}, stats.bucket(0.8));
        assertArrayEquals(new int[] {3, 0}, stats.bucket(0.86));
        assertEquals(0.6, stats.takeRate(0.1), 0.000_1);
    }

    @Test
    void fastTakesLowerTheFactorAndExpiriesRaiseIt() {
        var fast = stats(ended("DIRT", 0.8, 0, TAKEN, 10), ended("DIRT", 0.8, 1, TAKEN, 5));
        var slow = stats(ended("DIRT", 0.8, 0, EXPIRED, 120), ended("DIRT", 0.8, 1, TAKEN, 90));

        assertEquals(0.7, fast.factor(0.8, 0.5, 1.0), 0.000_1);
        assertEquals(0.85, slow.factor(0.8, 0.5, 1.0), 0.000_1);
    }

    @Test
    void onlyTheLastOrdersOfTheWindowMoveTheFactorWithinItsBounds() {
        settings.window(2);
        var orders = new ArrayList<Ended>();
        for (var day = 0; day < 6; day++) orders.add(ended("DIRT", 0.8, day, EXPIRED, 120));
        orders.add(ended("DIRT", 0.8, 6, TAKEN, 5));
        var stats = stats(orders.toArray(Ended[]::new));

        assertEquals(0.8, stats.factor(0.8, 0.5, 1.0), 0.000_1);
        settings.window(10);
        assertEquals(1.0, stats(orders.toArray(Ended[]::new)).factor(0.8, 0.5, 1.0), 0.000_1);
        assertEquals(0.5, stats(fastTakes(20)).factor(0.8, 0.5, 1.0), 0.000_1);
    }

    @Test
    void aMaterialWithoutTakesHasNoTimeToTake() {
        assertNull(stats(ended("DIRT", 0.8, 0, EXPIRED, 120)).minutesToTake());
    }

    @Test
    void theTakeRateNeverFallsBelowItsFloor() {
        var orders = new ArrayList<Ended>();
        for (var day = 0; day < 30; day++) orders.add(ended("DIRT", 0.8, day, EXPIRED, 120));

        assertEquals(0.1, stats(orders.toArray(Ended[]::new)).takeRate(0.1), 0.000_1);
    }

    @Test
    void aDrawFollowsTheWeights() {
        var weights = List.of(1.0, 3.0);

        assertEquals(0, GuessedLearning.draw(weights, () -> 0.2));
        assertEquals(1, GuessedLearning.draw(weights, () -> 0.3));
        assertEquals(1, GuessedLearning.draw(weights, () -> 0.99));
        assertEquals(-1, GuessedLearning.draw(List.of(), () -> 0.5));
    }

    private Ended[] fastTakes(int count) {
        var orders = new Ended[count];
        for (var day = 0; day < count; day++) orders[day] = ended("DIRT", 0.8, day, TAKEN, 1);
        return orders;
    }

    private GuessedLearning.Stats stats(Ended... orders) {
        return GuessedLearning.stats(List.of(orders), settings).get("DIRT");
    }

    private static Ended ended(String material, double factor, int day, String outcome, int minutes) {
        var placed = START.plusDays(day);
        return new Ended(material, factor, placed, outcome, outcome == null ? null : placed.plusMinutes(minutes));
    }
}
