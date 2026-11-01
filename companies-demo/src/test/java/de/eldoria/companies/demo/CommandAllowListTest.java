/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandAllowListTest {
    private final CommandAllowList list =
            new CommandAllowList(Set.of("company", "Order", "demo", "companies:company"), "no");

    @Test
    void allowsListedCommandsWithArgumentsPrefixAndAnyCase() {
        assertTrue(list.allows("/company create Stonebridge"));
        assertTrue(list.allows("/ORDER"));
        assertTrue(list.allows("companies:company info"));
        assertTrue(list.allows("demo"));
    }

    @Test
    void refusesEverythingElseAlsoBehindAPrefix() {
        assertFalse(list.allows("/give @s diamond 64"));
        assertFalse(list.allows("/minecraft:give @s diamond"));
        assertFalse(list.allows("/companyadmin"));
        assertFalse(list.allows("/"));
        assertFalse(list.allows("/essentials:order"));
        assertFalse(list.allows("/minecraft:company"));
    }
}
