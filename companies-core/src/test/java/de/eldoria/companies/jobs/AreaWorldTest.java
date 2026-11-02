/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.data.wrapper.job.JobArea;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The world of a stored area as the dashboard sees it: the top layer drawn with a shared palette, which areas are small
 * enough to draw, and blueprints that do not fit or whose world is missing.
 */
class AreaWorldTest {
    private static final JobArea FIELD = new JobArea("world", 10, 60, 20, 12, 70, 21);

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theTopLayerSharesOnePaletteRowByRow() {
        var top = AreaWorld.render(FIELD, (x, z) -> x == 11 ? null : z == 20 ? "#7fb238" : "#976d4d");

        assertEquals(3, top.width());
        assertEquals(2, top.length());
        assertEquals(List.of("#7fb238", "", "#976d4d"), top.palette());
        assertArrayEquals(new int[] {0, 1, 0, 2, 1, 2}, top.cells());
    }

    @Test
    void onlySmallAreasAreDrawn() {
        assertTrue(AreaWorld.drawable(new JobArea("world", 0, 0, 0, AreaWorld.MAX_SIDE - 1, 10, 5)));
        assertFalse(AreaWorld.drawable(new JobArea("world", 0, 0, 0, AreaWorld.MAX_SIDE, 10, 5)));
    }

    @Test
    void aPlacedBlueprintStandsOnTheGroundWithItsCornerWhereItWasPlaced() {
        var world = server.addSimpleWorld("world");
        var ground = world.getHighestBlockYAt(15, 26, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        var areaWorld = new AreaWorld(
                server, Runnable::run, (w, x, z) -> CompletableFuture.completedFuture(w.getChunkAt(x, z)));
        var area = new JobArea("world", 10, 0, 20, 30, 100, 40);
        var blueprint = Blueprint.of(List.of(
                new Blueprint.Cell(3, 0, 5, Material.STONE),
                new Blueprint.Cell(4, 0, 5, Material.STONE),
                new Blueprint.Cell(3, 1, 6, Material.OAK_PLANKS)));

        var placed = assertInstanceOf(
                AreaWorld.Placed.Done.class,
                areaWorld.place(area, blueprint, 5, 6).join());

        assertEquals(15, placed.area().minX());
        assertEquals(16, placed.area().maxX());
        assertEquals(26, placed.area().minZ());
        assertEquals(27, placed.area().maxZ());
        assertEquals(ground + 1, placed.area().minY());
    }

    @Test
    void aBlueprintMustLieInsideTheAreaAndItsWorldOnThisServer() {
        var world = new AreaWorld(server, Runnable::run);
        var blueprint = Blueprint.of(
                List.of(new Blueprint.Cell(0, 0, 0, Material.STONE), new Blueprint.Cell(1, 0, 0, Material.STONE)));

        assertInstanceOf(
                AreaWorld.Placed.Outside.class,
                world.place(FIELD, blueprint, 2, 0).join());
        assertInstanceOf(
                AreaWorld.Placed.Outside.class,
                world.place(FIELD, blueprint, -1, 0).join());
        assertInstanceOf(
                AreaWorld.Placed.NoWorld.class,
                world.place(FIELD, blueprint, 1, 1).join());
    }
}
