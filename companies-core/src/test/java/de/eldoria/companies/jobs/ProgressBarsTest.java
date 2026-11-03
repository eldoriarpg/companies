/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.components.job.JobState;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobLand;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Progress bars: a member standing in the area of a job their company works on sees how far it is, and the bar goes
 * when they leave or the job is no longer theirs.
 */
class ProgressBarsTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 12, 0);

    private ServerMock server;
    private WorldMock world;
    private final List<JobAccess.Active> active = new ArrayList<>();
    private final List<Integer> checked = new ArrayList<>();
    private ProgressBars bars;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        bars = new ProgressBars(
                MockBukkit.createMockPlugin(),
                () -> active,
                job -> {
                    checked.add(job.id());
                    return CompletableFuture.completedFuture(Optional.of(new DigCheck.Progress(40, 100, null)));
                },
                Runnable::run,
                (player, job, work) -> Component.text(job.title() + " " + work.percent()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aMemberInTheAreaSeesTheBarUntilTheyLeave() {
        var member = server.addPlayer();
        var stranger = server.addPlayer();
        var pit = job(7, JobKind.DIG);
        active.add(new JobAccess.Active(pit, Set.of(member.getUniqueId()), Set.of()));
        member.teleport(new Location(world, 2, 61, 2));
        stranger.teleport(new Location(world, 2, 61, 2));

        bars.refresh();

        assertEquals(7, bars.shownTo(member));
        assertEquals(-1, bars.shownTo(stranger));
        assertEquals(List.of(7), checked);

        member.teleport(new Location(world, 50, 61, 50));
        bars.refresh();

        assertEquals(-1, bars.shownTo(member));
    }

    @Test
    void harvestJobsAndContractsShowNoBar() {
        var member = server.addPlayer();
        active.add(new JobAccess.Active(job(8, JobKind.HARVEST), Set.of(member.getUniqueId()), Set.of()));
        active.add(new JobAccess.Active(job(9, JobKind.CONTRACT), Set.of(member.getUniqueId()), Set.of()));
        member.teleport(new Location(world, 2, 61, 2));

        assertNull(bars.jobAt(member));
        bars.refresh();

        assertEquals(-1, bars.shownTo(member));
        assertEquals(List.of(), checked);
    }

    @Test
    void theBarGoesWhenTheJobIsNoLongerWorkedOnHere() {
        var member = server.addPlayer();
        active.add(new JobAccess.Active(job(7, JobKind.BUILD), Set.of(member.getUniqueId()), Set.of()));
        member.teleport(new Location(world, 2, 61, 2));
        bars.refresh();
        assertEquals(7, bars.shownTo(member));

        active.clear();
        bars.refresh();

        assertEquals(-1, bars.shownTo(member));
    }

    @Test
    void aCheckThatEndsAfterThePlayerLeftShowsNothing() {
        var member = server.addPlayer();
        var pending = new CompletableFuture<Optional<DigCheck.Progress>>();
        var late = new ProgressBars(
                MockBukkit.createMockPlugin(),
                () -> active,
                job -> pending,
                Runnable::run,
                (player, job, work) -> Component.text(job.title()));
        active.add(new JobAccess.Active(job(7, JobKind.DIG), Set.of(member.getUniqueId()), Set.of()));
        member.teleport(new Location(world, 2, 61, 2));
        late.refresh();

        member.teleport(new Location(world, 50, 61, 50));
        pending.complete(Optional.of(new DigCheck.Progress(40, 100, null)));

        assertEquals(-1, late.shownTo(member));
    }

    private static Job job(int id, JobKind kind) {
        var draft = Job.draft(
                UUID.randomUUID(),
                kind,
                "Pit",
                100,
                5,
                UUID.randomUUID(),
                new JobArea("world", 0, 60, 0, 4, 64, 4),
                JobLand.AREA,
                "plotsquared:plots|1;1",
                24,
                NOW,
                NOW.plusDays(7));
        return new Job(
                id,
                draft.owner(),
                kind,
                draft.title(),
                null,
                draft.price(),
                draft.fee(),
                JobState.TAKEN,
                3,
                draft.node(),
                draft.area(),
                draft.land(),
                draft.landRef(),
                draft.dueHours(),
                NOW,
                draft.expires(),
                NOW,
                NOW.plusDays(1),
                null,
                null,
                true,
                draft.review(),
                null,
                null,
                draft.extension(),
                true,
                false,
                draft.materials());
    }
}
