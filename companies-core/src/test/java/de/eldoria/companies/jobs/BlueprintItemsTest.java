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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The items a blueprint takes: one item per block as a player places it, nothing for a second half, two slabs for a
 * double slab, two water buckets for all water, a lava bucket per lava source, and the blocks made by hand or by
 * nobody listed apart.
 */
class BlueprintItemsTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void blocksTakeTheItemsTheyArePlacedWith() {
        var needs = BlueprintItems.of(
                Blueprint.of(List.of(
                        new Blueprint.Cell(0, 0, 0, Material.OAK_DOOR, "minecraft:oak_door[facing=east,half=lower]"),
                        new Blueprint.Cell(0, 1, 0, Material.OAK_DOOR, "minecraft:oak_door[facing=east,half=upper]"),
                        new Blueprint.Cell(1, 0, 0, Material.RED_BED, "minecraft:red_bed[facing=east,part=foot]"),
                        new Blueprint.Cell(2, 0, 0, Material.RED_BED, "minecraft:red_bed[facing=east,part=head]"),
                        new Blueprint.Cell(3, 0, 0, Material.OAK_SLAB, "minecraft:oak_slab[type=double]"),
                        new Blueprint.Cell(4, 0, 0, Material.WALL_TORCH, "minecraft:wall_torch[facing=east]"),
                        new Blueprint.Cell(5, 0, 0, Material.WALL_TORCH, "minecraft:wall_torch[facing=west]"))),
                BlueprintItemsTest::placedWith);

        assertEquals(
                Map.of(Material.OAK_DOOR, 1, Material.RED_BED, 1, Material.OAK_SLAB, 2, Material.TORCH, 2),
                needs.items());
        assertEquals(Set.of(), needs.byHand());
        assertEquals(Map.of(), needs.fluids());
    }

    @Test
    void blocksOfSeveralPotsCakesAndPlantBodiesTakeWhatPlacesThem() {
        var needs = BlueprintItems.of(
                Blueprint.of(List.of(
                        new Blueprint.Cell(0, 0, 0, Material.CANDLE, "minecraft:candle[candles=3,lit=false]"),
                        new Blueprint.Cell(1, 0, 0, Material.SNOW, "minecraft:snow[layers=4]"),
                        new Blueprint.Cell(
                                2, 0, 0, Material.GLOW_LICHEN, "minecraft:glow_lichen[down=true,north=true,up=false]"),
                        new Blueprint.Cell(3, 0, 0, Material.POTTED_POPPY),
                        new Blueprint.Cell(4, 0, 0, Material.RED_CANDLE_CAKE),
                        new Blueprint.Cell(5, 0, 0, Material.KELP_PLANT),
                        new Blueprint.Cell(6, 0, 0, Material.ATTACHED_MELON_STEM))),
                Blueprint.Want::type);

        assertEquals(
                Map.of(
                        Material.CANDLE, 3,
                        Material.SNOW, 4,
                        Material.GLOW_LICHEN, 2,
                        Material.FLOWER_POT, 1,
                        Material.POPPY, 1,
                        Material.CAKE, 1,
                        Material.RED_CANDLE, 1,
                        Material.KELP, 1,
                        Material.MELON_SEEDS, 1),
                needs.items());
        assertEquals(Map.of(Material.WATER_BUCKET, 2), needs.fluids());
    }

    @Test
    void waterTakesTwoBucketsAndLavaOnePerSource() {
        var needs = BlueprintItems.of(
                Blueprint.of(List.of(
                        new Blueprint.Cell(0, 0, 0, Material.WATER, "minecraft:water[level=0]"),
                        new Blueprint.Cell(1, 0, 0, Material.WATER, "minecraft:water[level=0]"),
                        new Blueprint.Cell(2, 0, 0, Material.WATER, "minecraft:water[level=3]"),
                        new Blueprint.Cell(3, 0, 0, Material.OAK_STAIRS, "minecraft:oak_stairs[waterlogged=true]"),
                        new Blueprint.Cell(4, 0, 0, Material.LAVA, "minecraft:lava[level=0]"),
                        new Blueprint.Cell(5, 0, 0, Material.LAVA, "minecraft:lava[level=0]"),
                        new Blueprint.Cell(6, 0, 0, Material.LAVA, "minecraft:lava[level=2]"))),
                BlueprintItemsTest::placedWith);

        assertEquals(Map.of(Material.OAK_STAIRS, 1), needs.items());
        assertEquals(Map.of(Material.LAVA_BUCKET, 2, Material.WATER_BUCKET, 2), needs.fluids());
        assertEquals(Set.of(), needs.byHand());
    }

    @Test
    void blocksNoItemPlacesAreMadeByHandOrByNobody() {
        var needs = BlueprintItems.of(
                Blueprint.of(List.of(
                        new Blueprint.Cell(0, 0, 0, Material.FIRE),
                        new Blueprint.Cell(1, 0, 0, Material.FARMLAND),
                        new Blueprint.Cell(2, 0, 0, Material.SPAWNER),
                        new Blueprint.Cell(3, 0, 0, Material.INFESTED_STONE),
                        new Blueprint.Cell(4, 0, 0, Material.BUDDING_AMETHYST),
                        new Blueprint.Cell(5, 0, 0, Material.DIRT_PATH))),
                want -> want.type() == Material.FIRE ? null : want.type());

        assertEquals(Map.of(), needs.items());
        assertEquals(Set.of(Material.FIRE, Material.FARMLAND, Material.DIRT_PATH), needs.byHand());
        assertEquals(Set.of(Material.SPAWNER, Material.INFESTED_STONE, Material.BUDDING_AMETHYST), needs.unmakeable());
        assertTrue(BlueprintItems.unmakeable(Material.REINFORCED_DEEPSLATE));
    }

    @Test
    void onePlacingTakesWhatItsBlockNeeds() {
        assertEquals(
                List.of(new BlueprintItems.Take(Material.LAVA_BUCKET, 1)),
                BlueprintItems.take(new Blueprint.Want(Material.LAVA, "minecraft:lava[level=0]")));
        assertEquals(
                List.of(new BlueprintItems.Take(Material.FLOWER_POT, 1), new BlueprintItems.Take(Material.AZALEA, 1)),
                BlueprintItems.take(new Blueprint.Want(Material.POTTED_AZALEA_BUSH, "minecraft:potted_azalea_bush")));
        assertEquals(List.of(), BlueprintItems.take(new Blueprint.Want(Material.LAVA, "minecraft:lava[level=3]")));
        assertEquals(List.of(), BlueprintItems.take(new Blueprint.Want(Material.WATER, "minecraft:water[level=0]")));
        assertEquals(
                List.of(),
                BlueprintItems.take(
                        new Blueprint.Want(Material.OAK_DOOR, "minecraft:oak_door[facing=east,half=upper]")));
        assertEquals(List.of(), BlueprintItems.take(new Blueprint.Want(Material.SPAWNER, "minecraft:spawner")));
    }

    private static Material placedWith(Blueprint.Want want) {
        return want.type() == Material.WALL_TORCH ? Material.TORCH : want.type();
    }
}
