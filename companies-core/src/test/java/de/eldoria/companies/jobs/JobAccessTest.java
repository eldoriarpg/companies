/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.data.repository.impl.sqlite.SqLiteJobData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobHarvest;
import de.eldoria.companies.data.wrapper.job.JobKind;
import de.eldoria.companies.data.wrapper.job.JobLand;
import de.eldoria.companies.data.wrapper.job.JobState;
import de.eldoria.companies.data.wrapper.job.JobTarget;
import de.eldoria.companies.jobs.hooks.Claim;
import de.eldoria.companies.jobs.hooks.JobHooks;
import de.eldoria.companies.jobs.hooks.JobLandHook;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The access of a server's job areas: exactly the current members of a taken job's company, taken back from every
 * other granted job, with the players a hook added remembered so it removes only those.
 */
class JobAccessTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 12, 0);
    private static final UUID NODE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @TempDir
    Path directory;

    private final UUID ada = UUID.randomUUID();
    private final UUID bea = UUID.randomUUID();
    private final Map<Integer, Set<UUID>> members = new HashMap<>();
    private final Map<Integer, Set<UUID>> access = new HashMap<>();
    private final List<Integer> revoked = new ArrayList<>();
    private final List<Job> lost = new ArrayList<>();
    private final List<String> harvests = new ArrayList<>();
    private boolean owned = true;
    private boolean loaded = true;
    private SqLiteJobData jobs;
    private ServerMock server;
    private WorldMock world;
    private JobAccess sync;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        SqLiteTestDatabase.create(directory);
        jobs = new SqLiteJobData();
        var hooks = new JobHooks(name -> false, className -> new Object(), Logger.getAnonymousLogger());
        hooks.load();
        addHook(hooks);
        sync = new JobAccess(
                jobs,
                company -> members.getOrDefault(company, Set.of()),
                hooks,
                NODE,
                Runnable::run,
                lost::add,
                (job, company, worker, items) -> harvests.add(job + " " + worker + " " + items),
                new Blueprints(jobs, Logger.getAnonymousLogger()),
                Logger.getAnonymousLogger());
    }

    @Test
    void aTakenJobGivesExactlyTheCurrentMembersAccess() {
        var id = taken(3);
        members.put(3, Set.of(ada, bea));

        sync.check();

        assertEquals(Set.of(ada, bea), access.get(id));
        assertEquals(Set.of(ada, bea), jobs.grants(id));
        assertTrue(jobs.job(id).orElseThrow().granted());
        assertEquals(1, sync.active().size());

        members.put(3, Set.of(bea));
        sync.check();

        assertEquals(Set.of(bea), access.get(id));
        assertEquals(Set.of(bea), jobs.grants(id));
    }

    @Test
    void aJobThatEndedHasItsAccessTakenBack() {
        var id = taken(3);
        members.put(3, Set.of(ada));
        sync.check();
        jobs.close(id, JobState.TAKEN, JobState.PAID, null, NOW);

        sync.check();

        assertEquals(List.of(id), revoked);
        assertEquals(Set.of(), jobs.grants(id));
        assertFalse(jobs.job(id).orElseThrow().granted());
        assertEquals(List.of(), sync.active());
        sync.check();
        assertEquals(List.of(id), revoked);
    }

    @Test
    void aRevokeThatCanNotActYetIsTriedAgain() {
        var id = taken(3);
        members.put(3, Set.of(ada));
        sync.check();
        jobs.close(id, JobState.TAKEN, JobState.PAID, null, NOW);
        loaded = false;

        sync.check();

        assertTrue(jobs.job(id).orElseThrow().granted());
        assertEquals(Set.of(ada), jobs.grants(id));
        loaded = true;
        sync.check();
        assertFalse(jobs.job(id).orElseThrow().granted());
        assertEquals(List.of(id), revoked);
    }

    @Test
    void aJobWhoseLandChangedHandsIsHandedOnAndLosesItsAccess() {
        var id = taken(3);
        members.put(3, Set.of(ada));
        sync.check();
        owned = false;

        sync.check();

        assertEquals(List.of(id), lost.stream().map(Job::id).toList());
        assertEquals(List.of(id), revoked);
        assertEquals(List.of(), sync.active());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void playersWithOnlyJobAccessAreHeldToTheirAreasOnThePlot() {
        var first = taken(3);
        members.put(3, Set.of(ada));
        sync.check();
        var second = jobs.create(Job.draft(
                        UUID.randomUUID(),
                        JobKind.DIG,
                        "Other pit",
                        100,
                        5,
                        NODE,
                        new JobArea("world", 10, 60, 0, 14, 64, 4),
                        JobLand.AREA,
                        "plotsquared:plots|1;1",
                        24,
                        NOW,
                        NOW.plusDays(7)))
                .orElseThrow();
        jobs.take(second, 3, NOW, NOW.plusDays(1));
        sync.check();

        assertFalse(sync.outside(ada, world.getBlockAt(2, 62, 2)));
        assertFalse(sync.outside(ada, world.getBlockAt(12, 62, 2)));
        assertTrue(sync.outside(ada, world.getBlockAt(7, 62, 2)));
        assertFalse(sync.outside(bea, world.getBlockAt(7, 62, 2)));
        assertTrue(first > 0);
    }

    @Test
    void onlyTheWorkingCompanyPlacesInATakenArea() {
        taken(3);
        members.put(3, Set.of(ada));
        sync.check();

        assertFalse(sync.intruder(ada, world.getBlockAt(2, 62, 2)));
        assertTrue(sync.intruder(bea, world.getBlockAt(2, 62, 2)));
        assertFalse(sync.intruder(bea, world.getBlockAt(20, 62, 2)));
    }

    @Test
    void aDisputedAreaIsFrozenForEveryoneAndGivesNobodyAccess() {
        var id = taken(3);
        members.put(3, Set.of(ada));
        sync.check();
        jobs.dispute(id, 3, JobState.TAKEN, bea, "Damaged", NOW);

        sync.check();

        assertEquals(List.of(id), revoked);
        assertFalse(jobs.job(id).orElseThrow().granted());
        assertTrue(sync.intruder(ada, world.getBlockAt(2, 62, 2)));
        assertTrue(sync.intruder(bea, world.getBlockAt(2, 62, 2)));
        assertFalse(sync.outside(ada, world.getBlockAt(7, 62, 2)));
    }

    @Test
    void terraformWorkCountsWhatTheTargetAsksForAndEachFilledPositionOnce() {
        var worker = server.addPlayer();
        var id = jobs.create(Job.draft(
                                UUID.randomUUID(),
                                JobKind.TERRAFORM,
                                "Flat",
                                100,
                                5,
                                NODE,
                                new JobArea("world", 0, 60, 0, 4, 64, 4),
                                JobLand.AREA,
                                "plotsquared:plots|1;1",
                                24,
                                NOW,
                                NOW.plusDays(7))
                        .withTarget(new JobTarget(62, Material.GRASS_BLOCK.name())))
                .orElseThrow();
        jobs.take(id, 3, NOW, NOW.plusDays(1));
        members.put(3, Set.of(worker.getUniqueId()));
        sync.check();
        world.getBlockAt(1, 63, 1).setType(Material.STONE);
        world.getBlockAt(1, 62, 1).setType(Material.STONE);
        world.getBlockAt(1, 61, 1).setType(Material.STONE);
        world.getBlockAt(2, 62, 2).setType(Material.GRASS_BLOCK);

        breakBlock(worker, world.getBlockAt(1, 63, 1));
        breakBlock(worker, world.getBlockAt(1, 62, 1));
        breakBlock(worker, world.getBlockAt(1, 61, 1));
        breakBlock(worker, world.getBlockAt(2, 62, 2));
        place(worker, world.getBlockAt(3, 60, 3));
        breakBlock(worker, world.getBlockAt(3, 60, 3));
        place(worker, world.getBlockAt(3, 60, 3));
        place(worker, world.getBlockAt(3, 64, 3));
        place(worker, world.getBlockAt(1, 61, 1));
        place(worker, world.getBlockAt(4, 61, 4), Material.TORCH);
        place(worker, world.getBlockAt(4, 62, 4));
        place(worker, world.getBlockAt(1, 62, 1), Material.GRASS_BLOCK);
        world.getBlockAt(0, 60, 0).setType(Material.POPPY);
        breakBlock(worker, world.getBlockAt(0, 60, 0));
        place(worker, world.getBlockAt(0, 60, 0));
        sync.flush();

        assertEquals(Map.of(worker.getUniqueId(), 5), jobs.workOf(id));
    }

    @Test
    void aMemberHarvestsCaneAboveItsBaseIntoTheStockAndNothingElse() {
        var worker = server.addPlayer();
        var id = jobs.create(Job.draft(
                                UUID.randomUUID(),
                                JobKind.HARVEST,
                                "Cane",
                                100,
                                5,
                                NODE,
                                new JobArea("world", 0, 60, 0, 4, 64, 4),
                                JobLand.AREA,
                                "plotsquared:plots|1;1",
                                24,
                                NOW,
                                NOW.plusDays(7))
                        .withHarvest(new JobHarvest(1, 0)))
                .orElseThrow();
        jobs.take(id, 3, NOW, NOW.plusDays(1));
        members.put(3, Set.of(worker.getUniqueId()));
        sync.check();
        world.getBlockAt(1, 59, 1).setType(Material.SAND);
        for (var y = 60; y <= 62; y++) world.getBlockAt(1, y, 1).setType(Material.SUGAR_CANE);
        world.getBlockAt(2, 60, 2).setType(Material.FARMLAND);

        var base = new BlockBreakEvent(world.getBlockAt(1, 60, 1), worker);
        sync.onHarvest(base);
        var cane = new BlockBreakEvent(world.getBlockAt(1, 61, 1), worker);
        sync.onHarvest(cane);
        var soil = new BlockBreakEvent(world.getBlockAt(2, 60, 2), worker);
        sync.onHarvest(soil);
        sync.flush();

        assertTrue(base.isCancelled());
        assertTrue(cane.isCancelled());
        assertTrue(soil.isCancelled());
        assertEquals(Material.SUGAR_CANE, world.getBlockAt(1, 60, 1).getType());
        assertEquals(Material.AIR, world.getBlockAt(1, 61, 1).getType());
        assertEquals(Material.AIR, world.getBlockAt(1, 62, 1).getType());
        assertEquals(List.of(id + " " + worker.getUniqueId() + " {SUGAR_CANE=2}"), harvests);
    }

    @Test
    void membersOnlyPlantInAHarvestFieldAndWhatTheyPlacedIsNoHarvest() {
        var worker = server.addPlayer();
        var id = harvestJob();
        members.put(3, Set.of(worker.getUniqueId()));
        sync.check();
        var stone = world.getBlockAt(3, 61, 3);
        stone.setType(Material.COBBLESTONE);
        var wheat = world.getBlockAt(3, 61, 2);
        wheat.setType(Material.WHEAT);
        var cane = world.getBlockAt(2, 61, 2);
        world.getBlockAt(2, 60, 2).setType(Material.SUGAR_CANE);
        cane.setType(Material.SUGAR_CANE);

        var cobble = new BlockPlaceEvent(
                stone, stone.getState(), stone, new ItemStack(Material.COBBLESTONE), worker, true, EquipmentSlot.HAND);
        sync.onPlace(cobble);
        var seed = new BlockPlaceEvent(
                wheat, wheat.getState(), wheat, new ItemStack(Material.WHEAT_SEEDS), worker, true, EquipmentSlot.HAND);
        sync.onPlace(seed);
        place(worker, cane, Material.SUGAR_CANE);
        sync.onHarvest(new BlockBreakEvent(cane, worker));
        sync.flush();

        sync.onPlaced(seed);
        var ripe = (Ageable) wheat.getBlockData();
        ripe.setAge(ripe.getMaximumAge());
        wheat.setBlockData(ripe);
        var harvest = new BlockBreakEvent(wheat, worker);
        sync.onHarvest(harvest);

        assertTrue(cobble.isCancelled());
        assertFalse(seed.isCancelled());
        assertTrue(harvest.isCancelled());
        assertEquals(0, ((Ageable) wheat.getBlockData()).getAge());
        assertEquals(List.of(), harvests);
        assertTrue(id > 0);
    }

    @Test
    void aColumnIsHarvestedNoHigherThanTheField() {
        var worker = server.addPlayer();
        var id = harvestJob();
        members.put(3, Set.of(worker.getUniqueId()));
        sync.check();
        world.getBlockAt(1, 59, 1).setType(Material.SAND);
        for (var y = 60; y <= 66; y++) world.getBlockAt(1, y, 1).setType(Material.BAMBOO);

        sync.onHarvest(new BlockBreakEvent(world.getBlockAt(1, 61, 1), worker));
        sync.flush();

        assertEquals(List.of(id + " " + worker.getUniqueId() + " {BAMBOO=4}"), harvests);
        assertEquals(Material.BAMBOO, world.getBlockAt(1, 65, 1).getType());
    }

    @Test
    void buildWorkCountsBlocksPlacedAsTheBlueprintAsksAndWrongBlocksTakenOut() {
        var worker = server.addPlayer();
        var area = new JobArea("world", 0, 60, 0, 1, 60, 0);
        var id = jobs.create(Job.draft(
                        UUID.randomUUID(),
                        JobKind.BUILD,
                        "Wall",
                        100,
                        5,
                        NODE,
                        area,
                        JobLand.AREA,
                        "plotsquared:plots|1;1",
                        24,
                        NOW,
                        NOW.plusDays(7)))
                .orElseThrow();
        jobs.blueprint(
                id,
                Blueprint.of(List.of(
                                new Blueprint.Cell(0, 0, 0, Material.STONE),
                                new Blueprint.Cell(1, 0, 0, Material.STONE)))
                        .encode());
        jobs.take(id, 3, NOW, NOW.plusDays(1));
        members.put(3, Set.of(worker.getUniqueId()));
        sync.check();
        world.getBlockAt(1, 60, 0).setType(Material.DIRT);

        place(worker, world.getBlockAt(0, 60, 0), Material.STONE);
        breakBlock(worker, world.getBlockAt(0, 60, 0));
        place(worker, world.getBlockAt(0, 60, 0), Material.STONE);
        breakBlock(worker, world.getBlockAt(1, 60, 0));
        place(worker, world.getBlockAt(1, 60, 0), Material.GLASS);
        sync.flush();

        assertEquals(Map.of(worker.getUniqueId(), 2), jobs.workOf(id));
        var owner = server.addPlayer();
        var undo = new BlockBreakEvent(world.getBlockAt(0, 60, 0), owner);
        sync.onHold(undo);
        assertTrue(undo.isCancelled());
    }

    private int harvestJob() {
        var id = jobs.create(Job.draft(
                                UUID.randomUUID(),
                                JobKind.HARVEST,
                                "Field",
                                100,
                                5,
                                NODE,
                                new JobArea("world", 0, 60, 0, 4, 64, 4),
                                JobLand.AREA,
                                "plotsquared:plots|1;1",
                                24,
                                NOW,
                                NOW.plusDays(7))
                        .withHarvest(new JobHarvest(1, 0)))
                .orElseThrow();
        jobs.take(id, 3, NOW, NOW.plusDays(1));
        return id;
    }

    private void breakBlock(Player player, Block block) {
        sync.onBreak(new BlockBreakEvent(block, player));
    }

    private void place(Player player, Block block) {
        place(player, block, Material.DIRT);
    }

    private void place(Player player, Block block, Material type) {
        block.setType(type);
        sync.onPlaced(new BlockPlaceEvent(
                block, block.getState(), block, new ItemStack(type), player, true, EquipmentSlot.HAND));
    }

    private int taken(int company) {
        var id = jobs.create(Job.draft(
                        UUID.randomUUID(),
                        JobKind.DIG,
                        "Pit",
                        100,
                        5,
                        NODE,
                        new JobArea("world", 0, 60, 0, 4, 64, 4),
                        JobLand.AREA,
                        "plotsquared:plots|1;1",
                        24,
                        NOW,
                        NOW.plusDays(7)))
                .orElseThrow();
        jobs.take(id, company, NOW, NOW.plusDays(1));
        return id;
    }

    private void addHook(JobHooks hooks) {
        hooks.register(new JobLandHook() {
            @Override
            public String plugin() {
                return "Test";
            }

            @Override
            public Claim claim(Player owner, JobArea area) {
                return new Claim.None();
            }

            @Override
            public Set<UUID> grant(Job job, Collection<UUID> players, Set<UUID> granted) {
                access.put(job.id(), new HashSet<>(players));
                return Set.copyOf(players);
            }

            @Override
            public boolean revoke(Job job, Set<UUID> granted) {
                if (!loaded) return false;
                revoked.add(job.id());
                access.remove(job.id());
                return true;
            }

            @Override
            public boolean owns(Job job) {
                return owned;
            }

            @Override
            public boolean reaches(Job job, String world, int x, int y, int z) {
                return x >= 0 && x <= 15;
            }
        });
    }
}
