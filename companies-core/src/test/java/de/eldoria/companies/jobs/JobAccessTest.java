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
import de.eldoria.companies.data.wrapper.job.JobKind;
import de.eldoria.companies.data.wrapper.job.JobLand;
import de.eldoria.companies.data.wrapper.job.JobState;
import de.eldoria.companies.jobs.hooks.Claim;
import de.eldoria.companies.jobs.hooks.JobHooks;
import de.eldoria.companies.jobs.hooks.JobLandHook;
import org.bukkit.entity.Player;
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
                        JobLand.PLOT,
                        "1;1",
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

    private int taken(int company) {
        var id = jobs.create(Job.draft(
                        UUID.randomUUID(),
                        JobKind.DIG,
                        "Pit",
                        100,
                        5,
                        NODE,
                        new JobArea("world", 0, 60, 0, 4, 64, 4),
                        JobLand.PLOT,
                        "1;1",
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
