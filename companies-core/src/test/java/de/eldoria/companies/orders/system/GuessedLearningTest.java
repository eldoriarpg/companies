/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import de.eldoria.companies.configuration.elements.systemorders.GuessedSettings;
import de.eldoria.companies.orders.system.GuessedLearning.Ended;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static de.eldoria.companies.orders.system.GuessedLearning.EXPIRED;
import static de.eldoria.companies.orders.system.GuessedLearning.REPRICED;
import static de.eldoria.companies.orders.system.GuessedLearning.TAKEN;
import static de.eldoria.companies.orders.system.GuessedLearning.WITHDRAWN;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GuessedLearningTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 1, 8, 0);

    private final GuessedSettings settings = new GuessedSettings();

    @Test
    void outcomesAreCountedPerMaterial() {
        var stats = GuessedLearning.stats(
                        List.of(
                                ended("DIRT", 0.8, 0, TAKEN, 20),
                                ended("DIRT", 0.8, 1, TAKEN, 60),
                                ended("DIRT", 0.85, 2, REPRICED, 120),
                                ended("DIRT", 0.85, 3, EXPIRED, 120),
                                ended("DIRT", 0.85, 4, WITHDRAWN, 10),
                                ended("DIRT", 0.85, 5, null, 0),
                                ended("SAND", 0.8, 0, null, 0)),
                        settings)
                .get("DIRT");

        assertEquals(6, stats.posted());
        assertEquals(2, stats.taken());
        assertEquals(1, stats.expired());
        assertEquals(40.0, stats.minutesToTake());
        assertArrayEquals(new int[] {2, 2}, stats.bucket(0.8));
        assertArrayEquals(new int[] {4, 0}, stats.bucket(0.86));
        assertEquals(0.6, stats.takeRate(0.1), 0.000_1);
    }

    @Test
    void fastTakesLowerTheFactorAndExpiriesRaiseIt() {
        var fast = stats(ended("DIRT", 0.8, 0, TAKEN, 10), ended("DIRT", 0.8, 1, TAKEN, 5));
        var slow = stats(ended("DIRT", 0.8, 0, EXPIRED, 120), ended("DIRT", 0.8, 1, TAKEN, 90));

        assertEquals(0.7, fast.factor(), 0.000_1);
        assertEquals(-0.1, fast.offset(), 0.000_1);
        assertEquals(0.85, slow.factor(), 0.000_1);
    }

    @Test
    void whatWasLearnedStaysWhileOrdersSellAtThatPrice() {
        var orders = new ArrayList<Ended>();
        for (var day = 0; day < 4; day++) orders.add(ended("DIRT", 0.8, day, EXPIRED, 120));
        for (var day = 4; day < 30; day++) orders.add(ended("DIRT", 1.0, day, TAKEN, 60));

        assertEquals(1.0, stats(orders.toArray(Ended[]::new)).factor(), 0.000_1);
    }

    @Test
    void theWalkStaysWithinItsBounds() {
        var expiries = new ArrayList<Ended>();
        for (var day = 0; day < 20; day++) expiries.add(ended("DIRT", 0.8, day, EXPIRED, 120));
        var takes = new ArrayList<Ended>();
        for (var day = 0; day < 20; day++) takes.add(ended("DIRT", 0.8, day, TAKEN, 1));

        assertEquals(1.0, stats(expiries.toArray(Ended[]::new)).factor(), 0.000_1);
        assertEquals(0.5, stats(takes.toArray(Ended[]::new)).factor(), 0.000_1);
    }

    @Test
    void aConfiguredFactorBelowTheMinimumIsNeverRaisedByIt() {
        settings.factor(0.4);

        var stats = stats(ended("DIRT", 0.4, 0, TAKEN, 60));

        assertEquals(0.4, stats.factor(), 0.000_1);
        assertEquals(0.4, GuessedLearning.lowest(settings), 0.000_1);
    }

    @Test
    void repricesAndCappedExpiriesDoNotMoveTheFactor() {
        var stats = stats(
                ended("DIRT", 0.8, 0, REPRICED, 120),
                ended("DIRT", 0.8, 1, REPRICED, 120),
                new Ended(
                        "DIRT",
                        0.7,
                        true,
                        START.plusDays(2),
                        EXPIRED,
                        START.plusDays(2).plusHours(2)));

        assertEquals(0.8, stats.factor(), 0.000_1);
        assertEquals(1, stats.expired());
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

    private GuessedLearning.Stats stats(Ended... orders) {
        return GuessedLearning.stats(List.of(orders), settings).get("DIRT");
    }

    private static Ended ended(String material, double factor, int day, String outcome, int minutes) {
        var placed = START.plusDays(day);
        return new Ended(
                material, factor, false, placed, outcome, outcome == null ? null : placed.plusMinutes(minutes));
    }
}
