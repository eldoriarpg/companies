/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.chojo.sadu.queries.configuration.QueryConfiguration;
import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.components.job.JobState;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobExtension;
import de.eldoria.companies.data.wrapper.job.JobHarvest;
import de.eldoria.companies.data.wrapper.job.JobLand;
import de.eldoria.companies.data.wrapper.job.JobTarget;
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
        assertNull(job.target());
    }

    @Test
    void whetherAJobWasChargedIsStored() {
        var draft = Job.draft(
                OWNER,
                JobKind.DIG,
                "Free",
                500,
                0,
                NODE,
                new JobArea("world", 0, 60, 0, 5, 70, 5),
                JobLand.REGION,
                "home",
                48,
                NOW,
                NOW.plusDays(7));

        var free = jobs.create(draft.withCharged(false)).orElseThrow();
        var paid = jobs.create(draft).orElseThrow();

        assertFalse(jobs.job(free).orElseThrow().charged());
        assertTrue(jobs.job(paid).orElseThrow().charged());
    }

    @Test
    void theTargetOfATerraformJobIsStored() {
        var id = jobs.create(Job.draft(
                                OWNER,
                                JobKind.TERRAFORM,
                                "Flat",
                                500,
                                10,
                                NODE,
                                new JobArea("world", 0, 60, 0, 5, 70, 5),
                                JobLand.REGION,
                                "home",
                                48,
                                NOW,
                                NOW.plusDays(7))
                        .withTarget(new JobTarget(64, "GRASS_BLOCK")))
                .orElseThrow();

        assertEquals(
                new JobTarget(64, "GRASS_BLOCK"), jobs.job(id).orElseThrow().target());
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

    @Test
    void aContractIsReviewedDisputedAndSettled() {
        var id = created(JobKind.CONTRACT, NOW.plusDays(7));
        jobs.take(id, 3, NOW, NOW.plusHours(48));

        assertTrue(jobs.describe(created(JobKind.CONTRACT, NOW.plusDays(7)), "Paint it red"));
        assertFalse(jobs.describe(id, "Too late"));
        assertTrue(jobs.markDone(id, 3, WORKER, NOW.plusHours(1)));
        assertFalse(jobs.markDone(id, 3, WORKER, NOW.plusHours(1)));
        var done = jobs.job(id).orElseThrow();
        assertEquals(JobState.DONE, done.state());
        assertEquals(NOW.plusHours(1), done.review().doneAt());
        assertEquals(WORKER, done.review().doneBy());
        assertEquals(
                List.of(id),
                jobs.waiting(NOW.plusHours(1)).stream().map(Job::id).toList());
        assertEquals(List.of(), jobs.waiting(NOW));
        assertEquals(List.of(id), jobs.ofCompany(3).stream().map(Job::id).toList());

        assertTrue(jobs.reject(id, "Still white", NOW.plusHours(60)));
        var rejected = jobs.job(id).orElseThrow();
        assertEquals(JobState.TAKEN, rejected.state());
        assertEquals("Still white", rejected.review().rejected());
        assertNull(rejected.review().doneAt());
        assertEquals(NOW.plusHours(60), rejected.due());

        assertFalse(jobs.dispute(id, 3, JobState.DONE, OWNER, "Broken", NOW));
        assertFalse(jobs.dispute(id, 4, JobState.TAKEN, OWNER, "Broken", NOW));
        assertTrue(jobs.dispute(id, 3, JobState.TAKEN, OWNER, "Broken", NOW.plusHours(2)));
        assertEquals(List.of(id), jobs.disputed().stream().map(Job::id).toList());
        assertFalse(jobs.release(id, 3));
        assertEquals(List.of(id), jobs.ofCompany(3).stream().map(Job::id).toList());

        assertTrue(jobs.settle(id, 60, "Admin", NOW.plusHours(3)));
        assertFalse(jobs.settle(id, 40, "Admin", NOW.plusHours(3)));
        var settled = jobs.job(id).orElseThrow();
        assertEquals(JobState.SETTLED, settled.state());
        assertEquals(60, settled.review().settled());
        assertEquals("Broken", settled.review().dispute());
        assertEquals(OWNER, settled.review().disputeBy());
        assertEquals("Admin", settled.closedBy());
    }

    @Test
    void aWithdrawnDisputeGivesTheJobBackToItsCompany() {
        var id = created(NOW.plusDays(7));
        jobs.take(id, 3, NOW, NOW.plusHours(48));
        jobs.dispute(id, 3, JobState.TAKEN, OWNER, "Broken", NOW);

        assertEquals(List.of(id), jobs.access(NODE).stream().map(Job::id).toList());
        assertFalse(jobs.withdraw(id, WORKER, NOW.plusHours(50), null));
        assertTrue(jobs.withdraw(id, OWNER, NOW.plusHours(50), null));
        assertFalse(jobs.withdraw(id, OWNER, NOW.plusHours(50), null));
        var taken = jobs.job(id).orElseThrow();
        assertEquals(JobState.TAKEN, taken.state());
        assertEquals(NOW.plusHours(50), taken.due());
        assertNull(taken.review().dispute());
        assertFalse(jobs.closeHeld(id, 4, JobState.TAKEN, JobState.PAID, null, NOW));
        assertTrue(jobs.closeHeld(id, 3, JobState.TAKEN, JobState.PAID, null, NOW));
    }

    @Test
    void aDisputeOpenedOnFinishedWorkIsWithdrawnBackToWaitingForTheOwner() {
        var id = created(JobKind.CONTRACT, NOW.plusDays(7));
        jobs.take(id, 3, NOW, NOW.plusHours(48));
        jobs.markDone(id, 3, WORKER, NOW.plusHours(1));
        jobs.dispute(id, 3, JobState.DONE, OWNER, "Ugly", NOW.plusHours(2));

        assertTrue(jobs.withdraw(id, OWNER, NOW.plusHours(49), NOW.plusHours(1)));

        var waiting = jobs.job(id).orElseThrow();
        assertEquals(JobState.DONE, waiting.state());
        assertEquals(NOW.plusHours(1), waiting.review().doneAt());
        assertEquals(WORKER, waiting.review().doneBy());
        assertNull(waiting.review().dispute());
    }

    @Test
    void harvestedItemsArePaidOnlyWithinTheBudgetAndWhileTheCompanyHasTheJob() {
        var id = jobs.create(Job.draft(
                                OWNER,
                                JobKind.HARVEST,
                                "Wheat",
                                10,
                                1,
                                NODE,
                                new JobArea("world", 0, 60, 0, 5, 61, 5),
                                JobLand.REGION,
                                "home",
                                48,
                                NOW,
                                NOW.plusDays(7))
                        .withHarvest(new JobHarvest(0.5, 0)))
                .orElseThrow();

        assertFalse(jobs.harvested(id, 3, Map.of("WHEAT", 4), 2));
        jobs.take(id, 3, NOW, NOW.plusHours(48));
        assertFalse(jobs.harvested(id, 4, Map.of("WHEAT", 4), 2));
        assertTrue(jobs.harvested(id, 3, Map.of("WHEAT", 4, "WHEAT_SEEDS", 2), 3));
        assertTrue(jobs.harvested(id, 3, Map.of("WHEAT", 6), 3));
        assertFalse(jobs.harvested(id, 3, Map.of("WHEAT", 20), 10));
        jobs.stock(id, Map.of("WHEAT", 1));

        assertEquals(new JobHarvest(0.5, 6), jobs.job(id).orElseThrow().harvest());
        assertEquals(Map.of("WHEAT", 11, "WHEAT_SEEDS", 2), jobs.stock(id));
        assertTrue(jobs.takeStock(id, "WHEAT", 11));
        assertFalse(jobs.takeStock(id, "WHEAT_SEEDS", 3));
        assertEquals(Map.of("WHEAT_SEEDS", 2), jobs.stock(id));
    }

    @Test
    void theBlueprintOfABuildJobIsStoredOnce() {
        var id = created(JobKind.BUILD, NOW.plusDays(7));

        assertTrue(jobs.blueprint(id).isEmpty());
        assertTrue(jobs.blueprint(id, "H4sI"));
        assertEquals("H4sI", jobs.blueprint(id).orElseThrow());
    }

    @Test
    void contractsHaveNoLandAndOverlapNothing() {
        var contract = created(JobKind.CONTRACT, NOW.plusDays(7));
        var dig = created(NOW.plusDays(7));

        assertEquals(List.of(dig), jobs.access(NODE).stream().map(Job::id).toList());
        assertEquals(
                List.of(dig),
                jobs.overlapping(new JobArea("world", 0, 60, 10, 5, 70, 20)).stream()
                        .map(Job::id)
                        .toList());
        assertTrue(jobs.job(contract).isPresent());
    }

    @Test
    void aJobWaitingForApprovalIsReleasedWithoutItsReview() {
        var id = created(JobKind.CONTRACT, NOW.plusDays(7));
        jobs.take(id, 3, NOW, NOW.plusHours(48));
        jobs.markDone(id, 3, WORKER, NOW);

        assertTrue(jobs.release(id, 3));
        var open = jobs.job(id).orElseThrow();
        assertEquals(JobState.OPEN, open.state());
        assertNull(open.review().doneAt());
        assertNull(open.review().doneBy());
    }

    @Test
    void aTakenJobGetsMoreTimeOnceAndARequestIsAskedAndRefused() {
        var id = created(NOW.plusDays(7));
        var due = NOW.plusHours(48);
        jobs.take(id, 3, NOW, due);

        assertFalse(jobs.askExtension(id, 3, 6, "Too late", due, 2));
        assertTrue(jobs.askExtension(id, 3, 6, "Big area", NOW, 2));
        assertFalse(jobs.askExtension(id, 3, 6, "Again", NOW, 2));
        var asked = jobs.job(id).orElseThrow().extension();
        assertEquals(6, asked.askedHours());
        assertEquals("Big area", asked.reason());
        assertEquals(NOW, asked.askedAt());
        assertEquals(1, asked.requests());
        var stored = jobs.job(id).orElseThrow().due();
        assertFalse(jobs.extend(id, 3, stored, stored.plusHours(6), 6, NOW.plusMinutes(1), NOW));
        assertFalse(jobs.extend(id, 3, stored, stored.plusHours(6), 6, null, stored));
        assertTrue(jobs.extend(id, 3, stored, stored.plusHours(6), 6, NOW, NOW));
        assertFalse(jobs.extend(id, 3, stored, stored.plusHours(12), 6, null, NOW));
        var extended = jobs.job(id).orElseThrow();
        assertEquals(due.plusHours(6), extended.due());
        assertEquals(6, extended.extension().extendedHours());
        assertFalse(extended.extension().asked());
        assertTrue(jobs.askExtension(id, 3, 6, "More", NOW, 2));
        assertTrue(jobs.refuseExtension(id, 3));
        assertFalse(jobs.refuseExtension(id, 3));
        assertFalse(jobs.askExtension(id, 3, 6, "Third", NOW, 2));
        assertTrue(jobs.release(id, 3));
        assertEquals(JobExtension.NONE, jobs.job(id).orElseThrow().extension());
    }

    private int created(LocalDateTime expires) {
        return created(JobKind.DIG, expires);
    }

    private int created(JobKind kind, LocalDateTime expires) {
        return jobs.create(Job.draft(
                        OWNER,
                        kind,
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
