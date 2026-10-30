/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.chojo.sadu.queries.configuration.QueryConfiguration;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobKind;
import de.eldoria.companies.data.wrapper.job.JobLand;
import de.eldoria.companies.data.wrapper.job.JobState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What every dialect does with the table of jobs. The database is started once per dialect and emptied before each
 * test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class JobDataContract {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 12, 0);
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID WORKER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID NODE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private DataSource dataSource;
    private AJobData jobs;

    /**
     * Starts a migrated database.
     *
     * @return its data source
     */
    protected abstract DataSource start() throws Exception;

    /**
     * Stops the database.
     */
    protected abstract void stop();

    /**
     * @return the dialect's job table
     */
    protected abstract AJobData jobs();

    @BeforeAll
    void startDatabase() throws Exception {
        dataSource = start();
    }

    @BeforeEach
    void setUp() {
        QueryConfiguration.setDefault(QueryConfiguration.builder(dataSource).build());
        query("DELETE FROM job_grants").single().delete();
        query("DELETE FROM job_work").single().delete();
        query("DELETE FROM jobs").single().delete();
        jobs = jobs();
    }

    @AfterAll
    void stopDatabase() {
        if (dataSource != null) stop();
    }

    @Test
    void aJobIsStoredAndReadBack() {
        var id = created(NOW.plusDays(7));

        var job = jobs.job(id).orElseThrow();

        assertEquals(OWNER, job.owner());
        assertEquals(JobKind.DIG, job.kind());
        assertEquals("Cellar", job.title());
        assertEquals(500.0, job.price());
        assertEquals(JobState.OPEN, job.state());
        assertNull(job.company());
        assertEquals(NODE, job.node());
        assertEquals(new JobArea("world", -5, 60, 10, 5, 70, 20), job.area());
        assertEquals(JobLand.REGION, job.land());
        assertEquals("home", job.landRef());
        assertEquals(NOW, job.created());
        assertEquals(NOW.plusDays(7), job.expires());
        assertFalse(job.granted());
    }

    @Test
    void onlyOneCompanyTakesAnOpenJobThatHasNotExpired() {
        var id = created(NOW.plusDays(7));
        var expired = created(NOW.minusHours(1));

        assertTrue(jobs.take(id, 3, NOW, NOW.plusHours(48)));
        assertFalse(jobs.take(id, 4, NOW, NOW.plusHours(48)));
        assertFalse(jobs.take(expired, 4, NOW, NOW.plusHours(48)));
        var taken = jobs.job(id).orElseThrow();
        assertEquals(JobState.TAKEN, taken.state());
        assertEquals(3, taken.company());
        assertEquals(NOW.plusHours(48), taken.due());
        assertEquals(List.of(id), jobs.ofCompany(3).stream().map(Job::id).toList());
        assertEquals(List.of(expired), jobs.expired(NOW).stream().map(Job::id).toList());
        assertEquals(
                List.of(id),
                jobs.overdue(NOW.plusHours(49)).stream().map(Job::id).toList());
    }

    @Test
    void aReleasedJobIsOpenAgainWithoutTheWorkOfTheCompany() {
        var id = created(NOW.plusDays(7));
        jobs.take(id, 3, NOW, NOW.plusHours(48));
        jobs.work(id, 3, WORKER, 10);
        jobs.work(id, 3, WORKER, 5);
        jobs.work(id, 4, WORKER, 7);
        assertEquals(Map.of(WORKER, 15), jobs.workOf(id));

        assertFalse(jobs.release(id, 4));
        assertTrue(jobs.release(id, 3));

        var open = jobs.job(id).orElseThrow();
        assertEquals(JobState.OPEN, open.state());
        assertNull(open.company());
        assertNull(open.due());
        assertEquals(Map.of(), jobs.workOf(id));
        assertEquals(List.of(id), jobs.open(NOW, 10).stream().map(Job::id).toList());
    }

    @Test
    void aJobClosesOnlyFromTheStateItWasReadIn() {
        var id = created(NOW.plusDays(7));

        assertFalse(jobs.close(id, JobState.TAKEN, JobState.PAID, "Nora", NOW));
        assertTrue(jobs.close(id, JobState.OPEN, JobState.CANCELLED, "Nora", NOW));
        assertFalse(jobs.close(id, JobState.OPEN, JobState.EXPIRED, null, NOW));

        var closed = jobs.job(id).orElseThrow();
        assertEquals(JobState.CANCELLED, closed.state());
        assertEquals(NOW, closed.closed());
        assertEquals("Nora", closed.closedBy());
        assertEquals(List.of(), jobs.open(NOW, 10));
        assertEquals(List.of(id), jobs.ofOwner(OWNER, 10).stream().map(Job::id).toList());
    }

    @Test
    void theAccessOfANodeCoversRunningJobsAndThoseStillGranted() {
        var taken = created(NOW.plusDays(7));
        var closed = created(NOW.plusDays(7));
        var open = created(NOW.plusDays(7));
        jobs.take(taken, 3, NOW, NOW.plusHours(48));
        jobs.take(closed, 3, NOW, NOW.plusHours(48));
        jobs.granted(closed, true);
        jobs.close(closed, JobState.TAKEN, JobState.PAID, null, NOW);

        assertEquals(
                List.of(taken, closed, open),
                jobs.access(NODE).stream().map(Job::id).toList());
        assertEquals(List.of(), jobs.access(UUID.randomUUID()));
        jobs.granted(closed, false);
        assertEquals(
                List.of(taken, open), jobs.access(NODE).stream().map(Job::id).toList());
    }

    @Test
    void theGrantsOfAJobAreReplacedWhole() {
        var id = created(NOW.plusDays(7));
        var other = UUID.fromString("00000000-0000-0000-0000-000000000003");

        assertTrue(jobs.grants(id, Set.of(WORKER, other)));
        assertEquals(Set.of(WORKER, other), jobs.grants(id));
        jobs.grants(id, Set.of(other));
        assertEquals(Set.of(other), jobs.grants(id));
        jobs.grants(id, Set.of());
        assertEquals(Set.of(), jobs.grants(id));
    }

    @Test
    void overlappingJobsAreFoundWhileTheyRun() {
        var id = created(NOW.plusDays(7));

        assertEquals(
                List.of(id),
                jobs.overlapping(new JobArea("world", 5, 70, 20, 9, 80, 30)).stream()
                        .map(Job::id)
                        .toList());
        assertEquals(List.of(), jobs.overlapping(new JobArea("world", 6, 70, 20, 9, 80, 30)));
        assertEquals(List.of(), jobs.overlapping(new JobArea("nether", 0, 60, 10, 5, 70, 20)));
        jobs.close(id, JobState.OPEN, JobState.CANCELLED, "Nora", NOW);
        assertEquals(List.of(), jobs.overlapping(new JobArea("world", 0, 60, 10, 5, 70, 20)));
    }

    private int created(LocalDateTime expires) {
        return jobs.create(Job.draft(
                        OWNER,
                        JobKind.DIG,
                        "Cellar",
                        500,
                        10,
                        NODE,
                        new JobArea("world", 5, 70, 20, -5, 60, 10),
                        JobLand.REGION,
                        "home",
                        48,
                        NOW,
                        expires))
                .orElseThrow();
    }
}
