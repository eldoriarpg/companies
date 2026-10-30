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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
