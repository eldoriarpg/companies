/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobTarget;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The check of a terraform job: solid up to the target height, empty above, and the top layer of the surface block
 * when the target names one.
 */
class TerraformCheckTest {
    private static final JobArea COLUMN = new JobArea("world", 0, 60, 0, 0, 63, 0);

    private WorldMock world;

    @BeforeEach
    void setUp() {
        world = MockBukkit.mock().addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aColumnSolidUpToTheTargetAndEmptyAboveIsDone() {
        column(Material.STONE, Material.DIRT, Material.AIR, Material.AIR);

        var progress = DigCheck.progress(COLUMN, TerraformCheck.of(world, new JobTarget(61, null)));

        assertTrue(progress.done());
        assertEquals(4, progress.cleared());
    }

    @Test
    void aHoleBelowAndABlockAboveTheTargetAreNotDone() {
        column(Material.STONE, Material.WATER, Material.AIR, Material.DIRT);

        var progress = DigCheck.progress(COLUMN, TerraformCheck.of(world, new JobTarget(61, null)));

        assertFalse(progress.done());
        assertEquals(2, progress.cleared());
        assertEquals("0 63 0", progress.leftText());
    }

    @Test
    void theTopLayerMustBeTheSurfaceBlock() {
        column(Material.DIRT, Material.STONE, Material.AIR, Material.AIR);
        var target = new JobTarget(61, Material.GRASS_BLOCK.name());

        assertFalse(DigCheck.progress(COLUMN, TerraformCheck.of(world, target)).done());
        world.getBlockAt(0, 61, 0).setType(Material.GRASS_BLOCK);
        assertTrue(DigCheck.progress(COLUMN, TerraformCheck.of(world, target)).done());
    }

    @Test
    void unbreakableBlocksCountAsDoneEverywhere() {
        column(Material.STONE, Material.STONE, Material.AIR, Material.BEDROCK);

        assertTrue(DigCheck.progress(COLUMN, TerraformCheck.of(world, new JobTarget(61, null)))
                .done());
    }

    @Test
    void onlySolidBlocksAreSurfaces() {
        assertEquals(Material.GRASS_BLOCK, TerraformCheck.block("grass_block"));
        assertEquals(Material.STONE, TerraformCheck.block("minecraft:stone"));
        assertNull(TerraformCheck.block("torch"));
        assertNull(TerraformCheck.block("nonsense"));
        assertNull(TerraformCheck.block("bedrock"));
        assertNull(TerraformCheck.block("barrier"));
        assertNull(TerraformCheck.block("spawner"));
        assertNull(TerraformCheck.block("farmland"));
        assertNull(TerraformCheck.block("infested_stone"));
        assertNull(TerraformCheck.block("budding_amethyst"));
        assertNull(TerraformCheck.block("copper_block"));
        assertNull(TerraformCheck.block("red_concrete_powder"));
        assertEquals(Material.WAXED_COPPER_BLOCK, TerraformCheck.block("waxed_copper_block"));
        assertEquals(Material.OXIDIZED_COPPER, TerraformCheck.block("oxidized_copper"));
    }

    private void column(Material... fromBottom) {
        for (var i = 0; i < fromBottom.length; i++) {
            world.getBlockAt(0, 60 + i, 0).setType(fromBottom[i]);
        }
    }
}
