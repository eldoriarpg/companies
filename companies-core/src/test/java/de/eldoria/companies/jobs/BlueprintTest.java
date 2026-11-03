/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.data.wrapper.job.JobArea;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blueprints of build jobs: placed around the owner, read by position, checked, and stored as text.
 */
class BlueprintTest {
    private static final Blueprint HUT = Blueprint.of(List.of(
            new Blueprint.Cell(-1, 0, 0, Material.STONE),
            new Blueprint.Cell(0, 0, 0, Material.STONE),
            new Blueprint.Cell(1, 0, 0, Material.STONE),
            new Blueprint.Cell(0, 1, 0, Material.OAK_PLANKS),
            new Blueprint.Cell(0, 2, 2, Material.GLASS),
            new Blueprint.Cell(1, 1, 1, Material.AIR)));

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aBlueprintCoversItsBlocksAroundTheOwnersPosition() {
        var area = HUT.area("world", 10, 64, 10);

        assertEquals(new JobArea("world", 9, 64, 10, 11, 66, 12), area);
        assertArrayEquals(new int[] {3, 3, 3}, HUT.size());
        assertEquals(5, HUT.blocks());
        assertEquals(Material.STONE, HUT.at(area, 9, 64, 10));
        assertEquals(Material.OAK_PLANKS, HUT.at(area, 10, 65, 10));
        assertEquals(Material.GLASS, HUT.at(area, 10, 66, 12));
        assertNull(HUT.at(area, 11, 65, 11));
        assertNull(HUT.at(area, 8, 64, 10));
    }

    @Test
    void liquidsAndUnbreakableBlocksOfASchematicAskForNothing() {
        var pond = Blueprint.of(List.of(
                new Blueprint.Cell(0, 0, 0, Material.STONE),
                new Blueprint.Cell(1, 0, 0, Material.WATER),
                new Blueprint.Cell(2, 0, 0, Material.BEDROCK)));

        assertEquals(1, pond.blocks());
        assertNull(pond.at(pond.area("world", 0, 0, 0), 1, 0, 0));
    }

    @Test
    void theFluidSourcesOfASchematicAreKeptApartAndStored() {
        var pool = Blueprint.of(List.of(
                new Blueprint.Cell(0, 0, 0, Material.STONE),
                new Blueprint.Cell(1, 0, 0, Material.WATER, "minecraft:water[level=0]"),
                new Blueprint.Cell(2, 0, 0, Material.WATER, "minecraft:water[level=4]"),
                new Blueprint.Cell(3, 0, 0, Material.LAVA, "minecraft:lava[level=0]")));

        assertEquals(1, pool.blocks());
        assertEquals(
                List.of(new Blueprint.Fluid(1, 0, 0, Material.WATER), new Blueprint.Fluid(3, 0, 0, Material.LAVA)),
                pool.fluids());
        var stored = Blueprint.decode(pool.encode());
        assertEquals(pool.fluids(), stored.fluids());
        assertEquals(pool, stored);
    }

    @Test
    void aSourceBelowTheGroundIsDugOutAndKeptAtItsPlacedSpot() {
        var well = Blueprint.of(List.of(
                new Blueprint.Cell(0, 0, 0, Material.STONE),
                new Blueprint.Cell(0, -1, 0, Material.WATER, "minecraft:water[level=0]"),
                new Blueprint.Cell(5, 9, 5, Material.LAVA, "minecraft:lava[level=0]")));
        BuildCheck.Types ground = (x, y, z) -> y < 64 ? Material.DIRT : Material.AIR;

        var placed = well.dug("world", 0, 64, 0, ground, (x, z) -> 63);

        assertEquals(1, placed.digs());
        assertEquals(
                List.of(new Blueprint.Fluid(0, 0, 0, Material.WATER), new Blueprint.Fluid(5, 10, 5, Material.LAVA)),
                placed.fluids());
        assertEquals(new JobArea("world", 0, 63, 0, 5, 73, 5), placed.area("world", 0, 64, 0));
        var stored = Blueprint.decode(placed.encode());
        assertEquals(placed, stored);
        assertEquals(1, stored.digs());
    }

    @Test
    void aBubbleColumnIsAWaterSourceAndFluidsMakeBlueprintsDiffer() {
        var dry = Blueprint.of(List.of(new Blueprint.Cell(0, 0, 0, Material.STONE)));
        var wet = Blueprint.of(List.of(
                new Blueprint.Cell(0, 0, 0, Material.STONE),
                new Blueprint.Cell(0, 1, 0, Material.BUBBLE_COLUMN, "minecraft:bubble_column[drag=true]")));

        assertEquals(List.of(new Blueprint.Fluid(0, 1, 0, Material.WATER)), wet.fluids());
        assertFalse(dry.equals(Blueprint.of(
                List.of(new Blueprint.Cell(0, 0, 0, Material.STONE), new Blueprint.Cell(0, 0, 1, Material.WATER)))));
    }

    @Test
    void aBlueprintStoredBeforeFluidsWereKeptHasNone() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeInt(-2);
            out.writeInt(1);
            out.writeInt(1);
            out.writeInt(1);
            out.writeInt(1);
            out.writeUTF("minecraft:stone");
            out.writeShort(1);
        }

        var stored = Blueprint.decode(Base64.getEncoder().encodeToString(bytes.toByteArray()));

        assertEquals(List.of(), stored.fluids());
        assertEquals(1, stored.blocks());
        assertEquals(
                Blueprint.decode(Blueprint.of(List.of(new Blueprint.Cell(0, 0, 0, Material.STONE)))
                        .encode()),
                stored);
    }

    @Test
    void aStoredBlueprintReadsTheSameAgainstItsArea() {
        var area = HUT.area("world", 10, 64, 10);

        var stored = Blueprint.decode(HUT.encode());

        assertEquals(HUT, stored);
        assertNotEquals(HUT, Blueprint.of(List.of(new Blueprint.Cell(0, 0, 0, Material.STONE))));
        assertEquals(5, stored.blocks());
        for (var x = area.minX(); x <= area.maxX(); x++) {
            for (var y = area.minY(); y <= area.maxY(); y++) {
                for (var z = area.minZ(); z <= area.maxZ(); z++) {
                    assertEquals(HUT.at(area, x, y, z), stored.at(area, x, y, z));
                }
            }
        }
    }

    @Test
    void aBuildIsDoneWhenEnoughBlocksMatch() {
        var area = HUT.area("world", 10, 64, 10);

        var missingGlass =
                BuildCheck.progress(area, HUT, (x, y, z) -> y == 66 ? Material.AIR : HUT.at(area, x, y, z), 80);
        var missingTwo =
                BuildCheck.progress(area, HUT, (x, y, z) -> y >= 65 ? Material.AIR : HUT.at(area, x, y, z), 80);

        assertTrue(missingGlass.done());
        assertEquals(4, missingGlass.cleared());
        assertEquals(5, missingGlass.total());
        assertFalse(missingTwo.done());
        assertEquals("10 66 12", missingTwo.leftText());
    }

    @Test
    void aBlueprintFixesHowABlockIsTurnedButNotItsConnections() {
        var stair = new Blueprint.Want(
                Material.OAK_STAIRS, "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
        var fence = new Blueprint.Want(
                Material.OAK_FENCE,
                "minecraft:oak_fence[east=true,north=false,south=false,waterlogged=false,west=true]");
        var slab = new Blueprint.Want(Material.STONE_SLAB, "minecraft:stone_slab[type=top,waterlogged=false]");

        assertEquals(Map.of("facing", "north", "half", "bottom"), stair.fixed());
        assertTrue(stair.matches(
                Material.OAK_STAIRS,
                "minecraft:oak_stairs[facing=north,half=bottom,shape=outer_left,waterlogged=true]"));
        assertFalse(stair.matches(
                Material.OAK_STAIRS,
                "minecraft:oak_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"));
        assertFalse(stair.matches(Material.SPRUCE_STAIRS, "minecraft:spruce_stairs[facing=north,half=bottom]"));
        assertTrue(fence.fixed().isEmpty());
        assertTrue(fence.matches(Material.OAK_FENCE, "minecraft:oak_fence[east=false,north=true]"));
        assertEquals(Map.of("type", "top"), slab.fixed());
        assertFalse(slab.matches(Material.STONE_SLAB, "minecraft:stone_slab[type=bottom,waterlogged=false]"));
    }

    @Test
    void aBlockPlacedTurnedAnotherWayIsTurnedAsAsked() {
        var stair = new Blueprint.Want(
                Material.OAK_STAIRS, "minecraft:oak_stairs[facing=north,half=top,shape=straight,waterlogged=false]");
        var door = new Blueprint.Want(
                Material.OAK_DOOR, "minecraft:oak_door[facing=east,half=lower,hinge=left,open=false,powered=false]");

        assertEquals(
                "minecraft:oak_stairs[facing=north,half=top,shape=inner_left,waterlogged=true]",
                stair.turn(
                        Material.OAK_STAIRS,
                        "minecraft:oak_stairs[facing=west,half=bottom,shape=inner_left,waterlogged=true]"));
        assertNull(stair.turn(
                Material.OAK_STAIRS, "minecraft:oak_stairs[facing=north,half=top,shape=straight,waterlogged=false]"));
        assertNull(stair.turn(Material.STONE, "minecraft:stone"));
        assertNull(door.turn(
                Material.OAK_DOOR, "minecraft:oak_door[facing=west,half=lower,hinge=left,open=false,powered=false]"));
    }

    @Test
    void blocksOfTwoPartsJoinedChestsAndDoubleSlabsAreNeverTurned() {
        var bed = new Blueprint.Want(Material.RED_BED, "minecraft:red_bed[facing=north,occupied=false,part=foot]");
        var chest = new Blueprint.Want(Material.CHEST, "minecraft:chest[facing=north,type=single,waterlogged=false]");
        var top = new Blueprint.Want(Material.OAK_SLAB, "minecraft:oak_slab[type=top,waterlogged=false]");
        var full = new Blueprint.Want(Material.OAK_SLAB, "minecraft:oak_slab[type=double,waterlogged=false]");

        assertNull(bed.turn(Material.RED_BED, "minecraft:red_bed[facing=east,occupied=false,part=foot]"));
        assertNull(chest.turn(Material.CHEST, "minecraft:chest[facing=east,type=left,waterlogged=false]"));
        assertNull(full.turn(Material.OAK_SLAB, "minecraft:oak_slab[type=bottom,waterlogged=false]"));
        assertNull(top.turn(Material.OAK_SLAB, "minecraft:oak_slab[type=double,waterlogged=false]"));
        assertEquals(
                "minecraft:oak_slab[type=top,waterlogged=false]",
                top.turn(Material.OAK_SLAB, "minecraft:oak_slab[type=bottom,waterlogged=false]"));
    }

    @Test
    void aStoredBlueprintKeepsItsBlockDataAndCountsItsTypes() {
        var data = "minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]";
        var blueprint = Blueprint.of(List.of(
                new Blueprint.Cell(0, 0, 0, Material.OAK_STAIRS, data),
                new Blueprint.Cell(1, 0, 0, Material.OAK_STAIRS, data.replace("east", "west")),
                new Blueprint.Cell(2, 0, 0, Material.STONE)));
        var area = blueprint.area("world", 0, 0, 0);

        var stored = Blueprint.decode(blueprint.encode());

        assertEquals(data, stored.want(area, 0, 0, 0).data());
        assertEquals("west", stored.want(area, 1, 0, 0).fixed().get("facing"));
        assertEquals("minecraft:stone", stored.want(area, 2, 0, 0).data());
        assertEquals(Map.of(Material.OAK_STAIRS, 2, Material.STONE, 1), stored.counts());
        assertEquals(data, stored.cells().getFirst().data());
    }

    @Test
    void aBlueprintStoredBeforeBlockDataAsksForTypesAlone() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeInt(2);
            out.writeInt(1);
            out.writeInt(1);
            out.writeInt(2);
            out.writeUTF("OAK_STAIRS");
            out.writeUTF("NOT_A_BLOCK");
            out.writeShort(1);
            out.writeShort(2);
        }

        var stored = Blueprint.decode(Base64.getEncoder().encodeToString(bytes.toByteArray()));
        var area = stored.area("world", 0, 0, 0);

        assertEquals(1, stored.blocks());
        assertEquals(Material.OAK_STAIRS, stored.at(area, 0, 0, 0));
        assertTrue(stored.want(area, 0, 0, 0).fixed().isEmpty());
        assertNull(stored.at(area, 1, 0, 0));
    }

    @Test
    void aBuildChecksTheStatesTheBlueprintFixes() {
        var data = "minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]";
        var blueprint = Blueprint.of(List.of(new Blueprint.Cell(0, 0, 0, Material.OAK_STAIRS, data)));
        var area = blueprint.area("world", 0, 0, 0);
        BuildCheck.Types facingWest = new BuildCheck.Types() {
            @Override
            public Material at(int x, int y, int z) {
                return Material.OAK_STAIRS;
            }

            @Override
            public String data(int x, int y, int z) {
                return data.replace("east", "west");
            }
        };

        BuildCheck.Types facingEast = new BuildCheck.Types() {
            @Override
            public Material at(int x, int y, int z) {
                return Material.OAK_STAIRS;
            }

            @Override
            public String data(int x, int y, int z) {
                return data;
            }
        };

        assertFalse(BuildCheck.progress(area, blueprint, facingWest, 100).done());
        assertTrue(BuildCheck.progress(area, blueprint, facingEast, 100).done());
    }

    @Test
    void airBelowTheGroundIsDugOutAndTheRestAsksForNothing() {
        var cellar = Blueprint.of(
                List.of(new Blueprint.Cell(0, 0, 0, Material.STONE)),
                List.of(new int[] {0, -1, 0}, new int[] {0, -2, 0}, new int[] {1, 0, 0}, new int[] {0, 3, 0}));
        BuildCheck.Types ground = (x, y, z) -> y < 64 ? Material.DIRT : Material.AIR;

        var placed = cellar.dug("world", 0, 64, 0, ground, (x, z) -> 63);
        var area = placed.area("world", 0, 64, 0);

        assertEquals(1, placed.blocks());
        assertEquals(2, placed.digs());
        assertEquals(new JobArea("world", 0, 62, 0, 0, 64, 0), area);
        assertEquals(Blueprint.DIG, placed.want(area, 0, 63, 0));
        assertEquals(Map.of(Material.STONE, 1), placed.counts());
        var stored = Blueprint.decode(placed.encode());
        assertEquals(2, stored.digs());
        assertEquals(placed, stored);
        assertFalse(BuildCheck.progress(area, placed, (x, y, z) -> y == 64 ? Material.STONE : Material.DIRT, 100)
                .done());
        assertTrue(BuildCheck.progress(area, placed, (x, y, z) -> y == 64 ? Material.STONE : Material.AIR, 100)
                .done());
    }

    @Test
    void onlySolidGroundBelowTheSurfaceIsDugOut() {
        var house = Blueprint.of(
                List.of(new Blueprint.Cell(0, 0, 0, Material.STONE)),
                List.of(new int[] {0, -1, 0}, new int[] {1, 0, 0}, new int[] {2, 0, 0}, new int[] {0, 2, 0}));
        BuildCheck.Types world = (x, y, z) -> {
            if (y < 64) return x == 0 ? Material.WATER : Material.DIRT;
            if (x == 1) return Material.SHORT_GRASS;
            return y == 66 ? Material.OAK_LOG : Material.AIR;
        };

        var placed = house.dug("world", 0, 64, 0, world, (x, z) -> 63);

        assertEquals(0, placed.digs());
        assertEquals(1, placed.blocks());
    }

    @Test
    void aSchematicThatAsksForNothingIsRefused() {
        var pond = List.of(new Blueprint.Cell(0, 0, 0, Material.WATER), new Blueprint.Cell(1, 0, 0, Material.BEDROCK));

        assertThrows(IllegalArgumentException.class, () -> Blueprint.of(pond));
    }

    @Test
    void aHillTheBlueprintIsBuiltIntoIsDugOut() {
        var house = Blueprint.of(List.of(new Blueprint.Cell(0, 0, 0, Material.STONE)), List.of(new int[] {1, 1, 0}));
        BuildCheck.Types hill = (x, y, z) -> y <= 66 ? Material.DIRT : Material.AIR;

        var placed = house.dug("world", 0, 64, 0, hill, (x, z) -> 66);

        assertEquals(1, placed.digs());
        assertEquals(Blueprint.DIG, placed.want(placed.area("world", 0, 64, 0), 1, 65, 0));
    }
}
