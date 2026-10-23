/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelProvidersTest {
    @Test
    void levelNumbersRunFromOneToTheCount() {
        assertEquals(List.of(1, 2, 3), LevelProviders.levelNumbers(3, ""));
        assertEquals(List.of(1, 10, 11), LevelProviders.levelNumbers(11, "1"));
        assertTrue(LevelProviders.levelNumbers(0, "").isEmpty());
    }

    @Test
    void onlyTheTaxHasExampleValues() {
        assertEquals(List.of("none", "0", "5", "10"), LevelProviders.exampleValues("tax_percent"));
        assertEquals(List.of("none", "0", "5", "10"), LevelProviders.exampleValues("max_alerts"));
        assertTrue(LevelProviders.exampleValues("name").isEmpty());
        assertTrue(LevelProviders.exampleValues("").isEmpty());
    }
}
