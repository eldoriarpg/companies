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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blueprints of build jobs: placed around the owner, read by position, checked and shown layer by layer, and stored as
 * text.
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
    void layersCountWhatTheyStillLackAndTheLowestOpenOneComesFirst() {
        var area = HUT.area("world", 10, 64, 10);
        BuildCheck.Types onlyGround = (x, y, z) -> y == 64 ? Material.STONE : Material.AIR;

        assertEquals(new BuildGhosts.Layer(64, 3, 0), BuildGhosts.layer(area, HUT, onlyGround, 64));
        assertEquals(new BuildGhosts.Layer(65, 1, 1), BuildGhosts.layer(area, HUT, onlyGround, 65));
        assertEquals(65, BuildGhosts.firstOpen(area, HUT, onlyGround));
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
}
