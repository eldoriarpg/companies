/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.companylevel;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LevelProgressTest {
    @Test
    void progressIsTheShareOfTheRequirement() {
        Assertions.assertEquals(0.25, LevelProgress.of(250, 1000));
    }

    @Test
    void progressStopsAtOne() {
        Assertions.assertEquals(1.0, LevelProgress.of(5000, 1000));
    }

    @Test
    void nothingRequiredIsReached() {
        Assertions.assertEquals(1.0, LevelProgress.of(0, 0));
        Assertions.assertEquals(1.0, LevelProgress.of(3, -1));
    }

    @Test
    void negativeOrMissingValuesAreZero() {
        Assertions.assertEquals(0.0, LevelProgress.of(-5, 10));
        Assertions.assertEquals(0.0, LevelProgress.of(Double.NaN, 10));
    }
}
