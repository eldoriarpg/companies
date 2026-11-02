/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobLand;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ghost blocks of build jobs: a preview of the whole blueprint with a cooldown, and the layer view of the working
 * company, refreshed as blocks are placed and removed once the job is no longer worked on.
 */
class BuildGhostsTest {
    private static final Blueprint WALL = Blueprint.of(List.of(
            new Blueprint.Cell(0, 0, 0, Material.STONE),
            new Blueprint.Cell(1, 0, 0, Material.STONE),
            new Blueprint.Cell(0, 1, 0, Material.OAK_PLANKS)));

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-02T12:00:00Z"));
    private ServerMock server;
    private WorldMock world;
    private BuildGhosts ghosts;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        ghosts = new BuildGhosts(
                MockBukkit.createMockPlugin(),
                now::get,
                (world, x, y, z, data, dig) ->
                        world.spawnEntity(world.getBlockAt(x, y, z).getLocation(), EntityType.PIG));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aPreviewShowsTheWholeBlueprintOnceInACooldown() {
        var player = server.addPlayer();
        var area = WALL.area("world", 0, 60, 0);

        assertEquals(OptionalInt.of(3), ghosts.preview(player, area, WALL));
        assertEquals(3, displays());
        assertEquals(OptionalInt.empty(), ghosts.preview(player, area, WALL));
        assertEquals(OptionalLong.of(BuildGhosts.COOLDOWN_SECONDS), ghosts.cooldown(player));

        now.set(now.get().plusSeconds(BuildGhosts.COOLDOWN_SECONDS));
        assertTrue(ghosts.cooldown(player).isEmpty());
        ghosts.hidePreview(player.getUniqueId());
        assertEquals(0, displays());
    }

    @Test
    void aPreviewGoesAwayOnItsOwn() {
        var player = server.addPlayer();

        ghosts.preview(player, WALL.area("world", 0, 60, 0), WALL);
        server.getScheduler().performTicks(BuildGhosts.PREVIEW_SECONDS * 20L);

        assertEquals(0, displays());
    }

    @Test
    void aLayerLosesTheGhostOfABlockPlacedAsAskedAndGetsItBackWhenBroken() {
        var player = server.addPlayer();
        var job = job(WALL.area("world", 0, 60, 0));
        ghosts.show(player, job, WALL, 60);
        assertEquals(2, displays());

        var block = world.getBlockAt(0, 60, 0);
        block.setType(Material.STONE);
        ghosts.refresh(block);
        assertEquals(1, displays());

        block.setType(Material.AIR);
        ghosts.refresh(block);
        assertEquals(2, displays());
    }

    @Test
    void theGhostsOfAJobNoLongerWorkedOnAreRemoved() {
        var player = server.addPlayer();
        var job = job(WALL.area("world", 0, 60, 0));
        ghosts.show(player, job, WALL, 60);

        ghosts.prune(id -> id != job.id());

        assertEquals(0, displays());
        assertTrue(ghosts.layerOf(player, job.id()).isEmpty());
    }

    private long displays() {
        return world.getEntities().stream()
                .filter(entity -> entity.getType() == EntityType.PIG && entity.isValid())
                .count();
    }

    private static Job job(JobArea area) {
        var now = LocalDateTime.of(2026, 11, 2, 12, 0);
        return Job.draft(
                        UUID.randomUUID(),
                        JobKind.BUILD,
                        "Wall",
                        100,
                        5,
                        UUID.randomUUID(),
                        area,
                        JobLand.AREA,
                        "plotsquared:plots|1;1",
                        24,
                        now,
                        now.plusDays(7))
                .withId(7);
    }

    @Test
    void aPositionToDigShowsAsRedGlass() {
        assertEquals(Material.RED_STAINED_GLASS, BuildGhosts.data(Blueprint.DIG).getMaterial());
    }
}
