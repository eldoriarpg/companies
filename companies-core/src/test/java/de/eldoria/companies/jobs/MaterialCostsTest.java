/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the blocks of a blueprint cost: blocks per type times the price of one, types without a price left out.
 */
class MaterialCostsTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theCostIsTheBlocksOfEachTypeTimesItsPrice() {
        var prices = Map.of(Material.STONE, 2.5, Material.OAK_PLANKS, 4.0);
        var costs = new MaterialCosts(null, type -> prices.getOrDefault(type, 0.0));
        var blueprint = Blueprint.of(List.of(
                new Blueprint.Cell(0, 0, 0, Material.STONE),
                new Blueprint.Cell(1, 0, 0, Material.STONE),
                new Blueprint.Cell(2, 0, 0, Material.OAK_PLANKS),
                new Blueprint.Cell(3, 0, 0, Material.GLASS)));

        var cost = costs.of(blueprint);

        assertEquals(9.0, cost.total(), 1e-9);
        assertEquals(List.of(Material.GLASS), cost.unpriced());
        assertTrue(cost.above(8.99));
        assertFalse(cost.above(9.0));
    }

    @Test
    void blocksCostTheItemsTheyArePlacedWith() {
        var prices = Map.of(Material.OAK_DOOR, 10.0, Material.OAK_SLAB, 1.0, Material.TORCH, 0.5);
        var costs = new MaterialCosts(null, type -> prices.getOrDefault(type, 0.0), want -> switch (want.type()) {
            case WALL_TORCH -> Material.TORCH;
            case PISTON_HEAD -> null;
            default -> want.type();
        });
        var blueprint = Blueprint.of(List.of(
                new Blueprint.Cell(0, 0, 0, Material.OAK_DOOR, "minecraft:oak_door[facing=east,half=lower]"),
                new Blueprint.Cell(0, 1, 0, Material.OAK_DOOR, "minecraft:oak_door[facing=east,half=upper]"),
                new Blueprint.Cell(1, 0, 0, Material.OAK_SLAB, "minecraft:oak_slab[type=double]"),
                new Blueprint.Cell(2, 0, 0, Material.WALL_TORCH, "minecraft:wall_torch[facing=east]"),
                new Blueprint.Cell(3, 0, 0, Material.PISTON_HEAD, "minecraft:piston_head[facing=up]")));

        var cost = costs.of(blueprint);

        assertEquals(12.5, cost.total(), 1e-9);
        assertEquals(List.of(Material.PISTON_HEAD), cost.unpriced());
    }
}
