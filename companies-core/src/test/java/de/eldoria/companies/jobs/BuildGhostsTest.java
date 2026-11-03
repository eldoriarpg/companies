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
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
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
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ghost blocks of build jobs: a preview of the whole blueprint with a cooldown, the ghosts around a member of the
 * working company that follow them and are refreshed as blocks are placed, the highlight of every missing block with a
 * cooldown, and all of them removed once the job is no longer worked on.
 */
class BuildGhostsTest {
    private static final Blueprint WALL = Blueprint.of(List.of(
            new Blueprint.Cell(0, 0, 0, Material.STONE),
            new Blueprint.Cell(1, 0, 0, Material.STONE),
            new Blueprint.Cell(0, 1, 0, Material.OAK_PLANKS)));
    private static final Blueprint WIDE = Blueprint.of(IntStream.range(0, 64 * 64)
            .mapToObj(i -> new Blueprint.Cell(i % 64, 0, i / 64, Material.STONE))
            .toList());
    private static final Blueprint LINE = Blueprint.of(IntStream.range(0, 30)
            .mapToObj(x -> new Blueprint.Cell(x, 0, 0, Material.STONE))
            .toList());

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-02T12:00:00Z"));
    private ServerMock server;
    private WorldMock world;
    private BuildGhosts ghosts;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        for (var x = 0; x < 4; x++) {
            for (var z = 0; z < 4; z++) world.loadChunk(x, z);
        }
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
    void theGhostsAroundAMemberLoseABlockPlacedAsAskedAndGetItBackWhenBroken() {
        var player = server.addPlayer();
        player.teleport(world.getBlockAt(0, 61, 2).getLocation());
        var job = job(WALL.area("world", 0, 60, 0));

        assertEquals(OptionalInt.of(3), ghosts.show(player, job, WALL, 8));
        var block = world.getBlockAt(0, 60, 0);
        block.setType(Material.STONE);
        ghosts.refresh(block);
        assertEquals(2, displays());

        block.setType(Material.AIR);
        ghosts.refresh(block);
        assertEquals(3, displays());
    }

    @Test
    void theGhostsFollowTheMemberAndStayWithinTheRadius() {
        var player = server.addPlayer();
        player.teleport(world.getBlockAt(0, 60, 0).getLocation());
        var job = job(LINE.area("world", 0, 60, 0));

        ghosts.show(player, job, LINE, 3);
        assertEquals(4, displays());
        assertTrue(ghostXs().stream().allMatch(x -> x <= 3));

        player.teleport(world.getBlockAt(20, 60, 0).getLocation());
        ghosts.follow();

        assertEquals(7, displays());
        assertTrue(ghostXs().stream().allMatch(x -> x >= 17 && x <= 23));
    }

    @Test
    void aHighlightShowsEveryMissingBlockForAWhileOnceInACooldown() {
        var player = server.addPlayer();
        player.teleport(world.getBlockAt(0, 60, 0).getLocation());
        var job = job(LINE.area("world", 0, 60, 0));

        assertEquals(OptionalInt.of(30), ghosts.highlight(player, job, LINE));
        assertEquals(OptionalInt.empty(), ghosts.highlight(player, job, LINE));
        assertEquals(OptionalLong.of(BuildGhosts.COOLDOWN_SECONDS), ghosts.highlightCooldown(player));
        server.getScheduler().performTicks(BuildGhosts.HIGHLIGHT_SECONDS * 20L);

        assertEquals(0, displays());
    }

    @Test
    void theGhostsAroundTheMemberComeBackAfterAHighlight() {
        var player = at(0, 60, 0);
        var job = job(LINE.area("world", 0, 60, 0));
        ghosts.show(player, job, LINE, 3);

        ghosts.highlight(player, job, LINE);
        assertEquals(30, displays());
        server.getScheduler().performTicks(BuildGhosts.HIGHLIGHT_SECONDS * 20L);

        assertEquals(4, displays());
        ghosts.follow();
        assertEquals(4, displays());
    }

    @Test
    void theNearestGhostsAreKeptWhenThereAreTooMany() {
        var player = at(0, 60, 0);
        var job = job(WIDE.area("world", 0, 60, 0));

        ghosts.show(player, job, WIDE, 64);

        assertEquals(BuildGhosts.MAX_GHOSTS, displays());
        var farthest = world.getEntities().stream()
                .filter(entity -> entity.getType() == EntityType.PIG)
                .mapToInt(entity -> {
                    var x = entity.getLocation().getBlockX();
                    var z = entity.getLocation().getBlockZ();
                    return x * x + z * z;
                })
                .max()
                .orElseThrow();
        assertTrue(farthest <= 37 * 37);
    }

    @Test
    void aMemberWhoDidNotMoveKeepsTheSameGhosts() {
        var player = at(0, 60, 0);
        var job = job(LINE.area("world", 0, 60, 0));
        ghosts.show(player, job, LINE, 3);
        var before = world.getEntities().stream().map(Entity::getUniqueId).toList();

        ghosts.follow();

        assertEquals(
                before, world.getEntities().stream().map(Entity::getUniqueId).toList());
    }

    @Test
    void positionsPlacedFromTheStockShowNoGhost() {
        var player = at(0, 60, 0);
        var job = job(LINE.area("world", 0, 60, 0));
        ghosts.counted((id, key) -> id == job.id() && key == JobAccess.key(1, 60, 0));

        ghosts.show(player, job, LINE, 3);

        assertFalse(ghostXs().contains(1));
        ghosts.hide(player);
        now.set(now.get().plusSeconds(BuildGhosts.COOLDOWN_SECONDS));
        ghosts.highlight(player, job, LINE);
        assertEquals(29, displays());
    }

    @Test
    void aHighlightIgnoresFollowingAndTheGhostsAroundReplaceIt() {
        var player = at(0, 60, 0);
        var job = job(LINE.area("world", 0, 60, 0));
        ghosts.highlight(player, job, LINE);
        player.teleport(world.getBlockAt(20, 60, 0).getLocation());

        ghosts.follow();
        assertEquals(30, displays());
        ghosts.show(player, job, LINE, 3);

        assertEquals(7, displays());
    }

    @Test
    void quittingClearsTheHighlightCooldownAndGhostsStayInTheirWorld() {
        var player = at(0, 60, 0);
        var job = job(LINE.area("world", 0, 60, 0));
        ghosts.highlight(player, job, LINE);

        ghosts.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertTrue(ghosts.highlightCooldown(player).isEmpty());
        assertEquals(0, displays());

        var nether = server.addSimpleWorld("nether");
        player.teleport(nether.getBlockAt(0, 60, 0).getLocation());
        assertTrue(ghosts.show(player, job, LINE, 3).isEmpty());
    }

    private Player at(int x, int y, int z) {
        var player = server.addPlayer();
        player.teleport(world.getBlockAt(x, y, z).getLocation());
        return player;
    }

    @Test
    void theGhostsOfAJobNoLongerWorkedOnAreRemoved() {
        var player = server.addPlayer();
        player.teleport(world.getBlockAt(0, 61, 2).getLocation());
        var job = job(WALL.area("world", 0, 60, 0));
        ghosts.show(player, job, WALL, 8);

        ghosts.prune(id -> id != job.id());

        assertEquals(0, displays());
    }

    private List<Integer> ghostXs() {
        return world.getEntities().stream()
                .filter(entity -> entity.getType() == EntityType.PIG && entity.isValid())
                .map(entity -> entity.getLocation().getBlockX())
                .toList();
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
