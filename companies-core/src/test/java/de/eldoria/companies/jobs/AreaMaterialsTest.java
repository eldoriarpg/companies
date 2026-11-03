/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobTarget;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What an area job digs out and fills in: every breakable block of a dig job, what lies above a terraform job's height
 * and the wrong top layer, and the gaps at and below it with the surface on top and the fill below; the fill as a
 * blueprint spanning the whole area.
 */
class AreaMaterialsTest {
    private static final JobArea AREA = new JobArea("world", 0, 60, 0, 1, 62, 0);

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aDigJobDigsEveryBreakableBlock() {
        BuildCheck.Types types = (x, y, z) -> y == 60 ? Material.STONE : y == 61 ? Material.WATER : Material.BEDROCK;

        var counted = AreaMaterials.of(JobKind.DIG, AREA, null, Material.DIRT, types);

        assertEquals(Map.of(Material.STONE, 2), counted.dug());
        assertEquals(Map.of(), counted.fill());
    }

    @Test
    void aTerraformJobDigsAboveAndFillsBelowWithTheSurfaceOnTop() {
        var target = new JobTarget(61, Material.GRASS_BLOCK.name());
        BuildCheck.Types types = (x, y, z) -> {
            if (y == 62) return Material.OAK_LOG;
            if (y == 61) return x == 0 ? Material.STONE : Material.AIR;
            return x == 0 ? Material.AIR : Material.DIRT;
        };

        var counted = AreaMaterials.of(JobKind.TERRAFORM, AREA, target, Material.COBBLESTONE, types);

        assertEquals(Map.of(Material.OAK_LOG, 2, Material.STONE, 1), counted.dug());
        assertEquals(Map.of(Material.GRASS_BLOCK, 2, Material.COBBLESTONE, 1), counted.fill());
        var fill = counted.blueprint(AREA);
        assertNotNull(fill);
        assertEquals(Material.COBBLESTONE, fill.at(AREA, 0, 60, 0));
        assertEquals(Material.GRASS_BLOCK, fill.at(AREA, 1, 61, 0));
        assertNull(fill.at(AREA, 1, 60, 0));
        assertEquals(List.of(2, 3, 1), List.of(fill.size()[0], fill.size()[1], fill.size()[2]));
    }
}
