/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.components.job.JobState;
import de.eldoria.companies.configuration.elements.jobs.JobSettings;
import de.eldoria.companies.configuration.elements.ordersettings.OrderFee;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteCompanyData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteJobData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobExtension;
import de.eldoria.companies.data.wrapper.job.JobLand;
import de.eldoria.companies.data.wrapper.job.JobTarget;
import de.eldoria.companies.orders.OrderTable;
import de.eldoria.companies.util.TestExecutors;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The life of jobs: the price held when posting, taking with a work slot, giving back, cancelling and expiring with
 * the money going back, and the price split by the work of each member when the area checks out.
 */
class JobServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");
    private static final UUID NODE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final JobArea AREA = new JobArea("world", 0, 60, 0, 9, 64, 9);
    private static final JobArea POINT = new JobArea("world", 3, 64, 3, 3, 64, 3);
    private static final DigCheck.Progress DONE = new DigCheck.Progress(500, 500, null);

    @TempDir
    Path directory;

    private final UUID owner = UUID.randomUUID();
    private final UUID manager = UUID.randomUUID();
    private final UUID worker = UUID.randomUUID();
    private final Map<UUID, Double> balances = new HashMap<>();
    private final List<Double> collected = new ArrayList<>();
    private final List<Long> kept = new ArrayList<>();
    private final List<String> notices = new ArrayList<>();
    private final List<String> penalties = new ArrayList<>();
    private final JobSettings settings = new JobSettings();
    private ExecutorService executor;
    private SqLiteJobData jobs;
    private SqLiteCompanyData companies;
    private CompanyProfile company;
    private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private JobService service;
    private int slots = 2;
    private boolean gone;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(4);
        jobs = new SqLiteJobData();
        companies = new SqLiteCompanyData(executor);
        var id =
                companies.submitCompanyCreation("Diggers", JoinMode.INVITE_ONLY).join();
        companies
                .submitMemberUpdate(CompanyMember.of(id, manager, CompanyPermission.MANAGE_ORDERS.mask()))
                .join();
        companies.submitMemberUpdate(CompanyMember.of(id, worker, 0)).join();
        var simple = companies.retrieveCompanyById(id).join().orElseThrow();
        company = companies.retrieveCompanyProfile(simple).join().orElseThrow();
        balances.put(owner, 1000.0);
        service();
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
        MockBukkit.unmock();
    }

    @Test
    void postingTakesThePriceAndTheFee() {
        var job = posted(200);

        assertEquals(JobState.OPEN, job.state());
        assertEquals(200.0, job.price());
        assertEquals(10.0, job.fee());
        assertEquals(790.0, balances.get(owner), 0.001);
        assertEquals(NOW.atOffset(ZoneOffset.UTC).toLocalDateTime().plusDays(7), job.expires());
    }

    @Test
    void aJobIsRefusedWhenItBreaksTheRulesAndNothingIsCharged() {
        settings.digMaxVolume(10);

        assertEquals(JobService.Refusal.VOLUME, refusal(post(200, 24, AREA)));
        settings.digMaxVolume(32768);
        assertEquals(JobService.Refusal.DUE, refusal(post(200, 1000, AREA)));
        assertEquals(JobService.Refusal.PRICE, refusal(post(0, 24, AREA)));
        assertEquals(JobService.Refusal.FUNDS, refusal(post(5000, 24, AREA)));
        settings.enabled(false);
        assertEquals(JobService.Refusal.DISABLED, refusal(post(200, 24, AREA)));
        assertEquals(1000.0, balances.get(owner), 0.001);
    }

    @Test
    void onlyAManagerTakesAJobAndOnlyWithAFreeSlot() {
        var job = posted(200);

        assertEquals(JobService.Change.NO_PERMISSION, service.take(company, worker, job.id()));
        slots = 0;
        assertEquals(JobService.Change.LIMIT, service.take(company, manager, job.id()));
        slots = 2;
        assertEquals(JobService.Change.DONE, service.take(company, manager, job.id()));
        assertEquals(JobService.Change.CLOSED, service.take(company, manager, job.id()));
        assertEquals(List.of("owner TAKEN " + job.id()), notices);
        var taken = jobs.job(job.id()).orElseThrow();
        assertEquals(company.id(), taken.company());
        assertEquals(taken.taken().plusHours(24), taken.due());
    }

    @Test
    void theOwnerGivesATakenJobMoreTimeWithinTheTotal() {
        var job = posted(200);
        assertEquals(JobService.Change.CLOSED, service.extend(owner, job.id(), 10));
        service.take(company, manager, job.id());
        var taken = jobs.job(job.id()).orElseThrow();

        assertEquals(JobService.Change.UNKNOWN, service.extend(manager, job.id(), 10));
        assertEquals(JobService.Change.HOURS, service.extend(owner, job.id(), 313));
        assertEquals(JobService.Change.DONE, service.extend(owner, job.id(), 12));

        var extended = jobs.job(job.id()).orElseThrow();
        assertEquals(taken.due().plusHours(12), extended.due());
        assertEquals(12, extended.extension().extendedHours());
        assertEquals(300, service.hoursLeft(extended));
        assertTrue(notices.contains("company EXTENDED " + job.id() + " 12"));
    }

    @Test
    void aCompanyAsksForMoreTimeAndTheOwnerGrantsOrRefuses() {
        var job = posted(200);
        service.take(company, manager, job.id());
        var due = jobs.job(job.id()).orElseThrow().due();

        assertEquals(JobService.Change.NO_PERMISSION, service.askExtension(company, worker, job.id(), 6, "Big"));
        assertEquals(JobService.Change.REASON, service.askExtension(company, manager, job.id(), 6, " "));
        assertEquals(JobService.Change.NO_REQUEST, service.grantExtension(owner, job.id()));
        assertEquals(JobService.Change.DONE, service.askExtension(company, manager, job.id(), 6, "Big area"));
        assertEquals(JobService.Change.ASKED, service.askExtension(company, manager, job.id(), 6, "Again"));
        assertTrue(notices.contains("owner EXTENSION_ASKED " + job.id()));
        assertEquals(JobService.Change.DONE, service.grantExtension(owner, job.id()));
        var granted = jobs.job(job.id()).orElseThrow();
        assertEquals(due.plusHours(6), granted.due());
        assertFalse(granted.extension().asked());

        assertEquals(JobService.Change.DONE, service.askExtension(company, manager, job.id(), 6, "More"));
        assertEquals(JobService.Change.DONE, service.refuseExtension(owner, job.id(), "No"));
        assertTrue(notices.contains("company REFUSED " + job.id() + " No"));
        assertEquals(JobService.Change.DONE, service.askExtension(company, manager, job.id(), 6, "Please"));
        assertEquals(JobService.Change.DONE, service.refuseExtension(owner, job.id(), ""));
        assertEquals(JobService.Change.REQUESTS, service.askExtension(company, manager, job.id(), 6, "Last"));
        assertEquals(due.plusHours(6), jobs.job(job.id()).orElseThrow().due());
    }

    @Test
    void aJobWhoseTimeRanOutGetsNoMoreTime() {
        var job = posted(200);
        service.take(company, manager, job.id());
        clock = Clock.offset(clock, Duration.ofHours(25));
        service();

        assertEquals(JobService.Change.CLOSED, service.extend(owner, job.id(), 5));
        assertEquals(JobService.Change.CLOSED, service.askExtension(company, manager, job.id(), 5, "Late"));
    }

    @Test
    void aRequestEndsWithTheTakenStateAndTheNextCompanyStartsAfresh() {
        var job = posted(200);
        service.take(company, manager, job.id());
        service.extend(owner, job.id(), 5);
        service.askExtension(company, manager, job.id(), 6, "Big area");

        service.giveBack(company, manager, job.id());
        service.take(company, manager, job.id());

        assertEquals(JobExtension.NONE, jobs.job(job.id()).orElseThrow().extension());
        settings.extensionsEnabled(false);
        assertEquals(JobService.Change.DISABLED, service.extend(owner, job.id(), 5));
        assertEquals(JobService.Change.DISABLED, service.askExtension(company, manager, job.id(), 6, "Big area"));
    }

    @Test
    void aCompanyCanNotTakeTheJobOfItsOwnMember() {
        var job = service.post(manager, JobKind.DIG, "Pit", 100, 24, NODE, AREA, JobLand.REGION, "home");
        balances.put(manager, 0.0);

        assertEquals(JobService.Refusal.FUNDS, refusal(job));
        balances.put(manager, 500.0);
        var own = ((JobService.Posted.Done)
                        service.post(manager, JobKind.DIG, "Pit", 100, 24, NODE, AREA, JobLand.REGION, "home"))
                .job();
        assertEquals(JobService.Change.OWN, service.take(company, manager, own.id()));
    }

    @Test
    void aGivenBackJobIsOpenAgain() {
        var job = posted(200);
        service.take(company, manager, job.id());

        assertEquals(JobService.Change.DONE, service.giveBack(company, manager, job.id()));
        assertEquals(JobState.OPEN, jobs.job(job.id()).orElseThrow().state());
        assertEquals(List.of(company.id() + " gave back"), penalties);
        assertEquals(JobService.Change.NOT_YOURS, service.giveBack(company, manager, job.id()));
    }

    @Test
    void cancellingGivesThePriceBackAndKeepsTheFee() {
        var job = posted(200);

        assertEquals(JobService.Change.NOT_YOURS, service.cancel(manager, "Mira", job.id()));
        assertEquals(JobService.Change.DONE, service.cancel(owner, "Ola", job.id()));

        assertEquals(990.0, balances.get(owner), 0.001);
        assertEquals(List.of(10.0), collected);
        assertEquals(JobState.CANCELLED, jobs.job(job.id()).orElseThrow().state());
        assertEquals(JobService.Change.CLOSED, service.cancel(owner, "Ola", job.id()));
    }

    @Test
    void anUnchargedJobTakesNothingAndRefundsNothing() {
        var posted = service.post(
                owner,
                JobKind.DIG,
                "Cellar",
                200,
                24,
                NODE,
                AREA,
                JobLand.REGION,
                "home",
                JobService.Details.NONE,
                false);
        var job = assertInstanceOf(JobService.Posted.Done.class, posted).job();

        assertEquals(1000.0, balances.get(owner), 0.001);
        assertEquals(0.0, job.fee());
        assertFalse(jobs.job(job.id()).orElseThrow().charged());
        assertEquals(JobService.Change.DONE, service.cancel(owner, "Ola", job.id()));
        assertEquals(1000.0, balances.get(owner), 0.001);

        var lost = assertInstanceOf(
                        JobService.Posted.Done.class,
                        service.post(
                                owner,
                                JobKind.DIG,
                                "Cellar",
                                200,
                                24,
                                NODE,
                                AREA,
                                JobLand.REGION,
                                "home",
                                JobService.Details.NONE,
                                false))
                .job();
        assertTrue(service.landLost(lost, null));
        assertEquals(1000.0, balances.get(owner), 0.001);
    }

    @Test
    void anUnchargedJobThatExpiresRefundsNothing() {
        var job = uncharged(JobKind.DIG, AREA, JobService.Details.NONE);
        clock = Clock.fixed(NOW.plusSeconds(8L * 24 * 3600), ZoneOffset.UTC);
        service();

        assertEquals(List.of(job.id()), service.check().expired());

        assertEquals(1000.0, balances.get(owner), 0.001);
    }

    @Test
    void anUnchargedTakenJobWhoseLandChangedHandsStillPaysTheWorkers() {
        var job = uncharged(JobKind.DIG, AREA, JobService.Details.NONE);
        service.take(company, manager, job.id());
        jobs.work(job.id(), company.id(), worker, 10);
        var taken = jobs.job(job.id()).orElseThrow();

        assertTrue(service.landLost(taken, new DigCheck.Progress(300, 500, new int[] {0, 64, 0})));

        assertEquals(1000.0, balances.get(owner), 0.001);
        assertEquals(86.4, balances.get(worker), 0.001);
    }

    @Test
    void anUnchargedHarvestThatEndsGivesNoBudgetBack() {
        var job = uncharged(JobKind.HARVEST, AREA, new JobService.Details(null, 1.0, null, null));
        service.take(company, manager, job.id());
        service.harvested(job.id(), company.id(), worker, Map.of("WHEAT", 4));
        clock = Clock.offset(clock, Duration.ofHours(25));
        service();

        assertEquals(List.of(job.id()), service.check().ended());

        assertEquals(1000.0, balances.get(owner), 0.001);
    }

    @Test
    void anUnchargedDisputeSettledForTheOwnerRefundsNothing() {
        var job = uncharged(JobKind.CONTRACT, POINT, JobService.Details.NONE);
        service.take(company, manager, job.id());
        service.disputeAsCompany(company, manager, job.id(), "Owner blocks us");

        assertEquals(JobService.Change.DONE, service.settle("Admin", job.id(), 0));

        assertEquals(1000.0, balances.get(owner), 0.001);
    }

    private Job uncharged(JobKind kind, JobArea area, JobService.Details details) {
        var land = kind == JobKind.CONTRACT ? JobLand.NONE : JobLand.REGION;
        var ref = kind == JobKind.CONTRACT ? null : "home";
        return ((JobService.Posted.Done)
                        service.post(owner, kind, "Free", 200, 24, NODE, area, land, ref, details, false))
                .job();
    }

    @Test
    void aTakenJobCanNotBeCancelled() {
        var job = posted(200);
        service.take(company, manager, job.id());

        assertEquals(JobService.Change.TAKEN, service.cancel(owner, "Ola", job.id()));
    }

    @Test
    void anUnfinishedAreaIsNotPaid() {
        var job = posted(200);
        service.take(company, manager, job.id());
        var progress = new DigCheck.Progress(400, 500, new int[] {1, 64, 1});

        var finished = service.finish(company, manager, job.id(), progress);

        assertEquals(
                progress,
                assertInstanceOf(JobService.Finished.NotDone.class, finished).progress());
        assertEquals(JobState.TAKEN, jobs.job(job.id()).orElseThrow().state());
        assertEquals(List.of(), collected);
    }

    @Test
    void aFinishedJobIsPaidByTheWorkOfEachMemberWithTaxAndShare() {
        var job = posted(200);
        service.take(company, manager, job.id());
        jobs.work(job.id(), company.id(), worker, 300);
        jobs.work(job.id(), company.id(), manager, 100);

        var finished = service.finish(company, manager, job.id(), DONE);

        assertInstanceOf(JobService.Finished.Paid.class, finished);
        assertEquals(JobState.PAID, jobs.job(job.id()).orElseThrow().state());
        assertEquals(108.0, balances.get(worker), 0.001);
        assertEquals(36.0, balances.get(manager), 0.001);
        assertEquals(30.0, collected.stream().mapToDouble(Double::doubleValue).sum(), 0.001);
        assertEquals(List.of(3600L), kept);
        assertTrue(notices.contains("owner DONE " + job.id()));
        assertTrue(notices.contains("company PAID " + job.id()));
        assertEquals(
                JobService.Change.NOT_YOURS,
                assertInstanceOf(JobService.Finished.Refused.class, service.finish(company, manager, job.id(), DONE))
                        .change());
    }

    @Test
    void theFinisherGetsAllWhenNoWorkWasCounted() {
        var job = posted(100);
        service.take(company, manager, job.id());

        service.finish(company, manager, job.id(), DONE);

        assertEquals(72.0, balances.get(manager), 0.001);
    }

    @Test
    void theCheckExpiresOpenJobsAndOpensOverdueOnesAgainUntilTheirOwnTimeRunsOut() {
        var open = posted(100);
        var taken = ((JobService.Posted.Done) post(200, 24, new JobArea("world", 20, 60, 20, 29, 64, 29))).job();
        service.take(company, manager, taken.id());
        clock = Clock.fixed(NOW.plusSeconds(8L * 24 * 3600), ZoneOffset.UTC);
        service();

        var check = service.check();

        assertEquals(List.of(open.id()), check.expired());
        assertEquals(List.of(taken.id()), check.reopened());
        assertEquals(JobState.EXPIRED, jobs.job(open.id()).orElseThrow().state());
        assertEquals(JobState.OPEN, jobs.job(taken.id()).orElseThrow().state());
        assertEquals(1000.0 - 210.0, balances.get(owner), 0.001);
        assertTrue(notices.contains("overdue " + taken.id()));
        assertEquals(List.of(company.id() + " overdue"), penalties);
        assertEquals(List.of(taken.id()), service.check().expired());
        assertEquals(1000.0, balances.get(owner), 0.001);
        assertEquals(List.of(), service.check().expired());
    }

    @Test
    void thePartsAddUpToThePriceInCents() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var c = UUID.randomUUID();

        var parts = JobService.parts(100, Map.of(a, 1, b, 1, c, 1), a);

        assertEquals(
                10000L,
                Math.round(
                        parts.values().stream().mapToDouble(Double::doubleValue).sum() * 100));
        assertEquals(3, parts.size());
        assertEquals(Map.of(b, 12.34), JobService.parts(12.345, Map.of(), b));
    }

    @Test
    void aPriceBelowACentAndAnOverlappingAreaAreRefused() {
        assertEquals(JobService.Refusal.PRICE, refusal(post(0.004, 24, AREA)));
        posted(100);

        assertEquals(JobService.Refusal.OVERLAP, refusal(post(100, 24, new JobArea("world", 9, 64, 9, 20, 70, 20))));
        assertInstanceOf(JobService.Posted.Done.class, post(100, 24, new JobArea("world", 10, 60, 10, 12, 62, 12)));
    }

    @Test
    void aWaitingJobWhoseLandChangedHandsGivesThePriceBack() {
        var job = posted(200);

        assertTrue(service.landLost(job, null));

        var closed = jobs.job(job.id()).orElseThrow();
        assertEquals(JobState.CANCELLED, closed.state());
        assertEquals(JobService.LAND_LOST_BY, closed.closedBy());
        assertEquals(990.0, balances.get(owner), 0.001);
        assertEquals(List.of(10.0), collected);
        assertTrue(!service.landLost(closed, null));
    }

    @Test
    void aTakenJobWhoseLandChangedHandsPaysTheClearedShare() {
        var job = posted(200);
        service.take(company, manager, job.id());
        jobs.work(job.id(), company.id(), worker, 10);
        var taken = jobs.job(job.id()).orElseThrow();

        assertTrue(service.landLost(taken, new DigCheck.Progress(300, 500, new int[] {0, 64, 0})));

        assertEquals(JobState.CANCELLED, jobs.job(job.id()).orElseThrow().state());
        assertEquals(790.0 + 80.0, balances.get(owner), 0.001);
        assertEquals(86.4, balances.get(worker), 0.001);
        assertTrue(notices.contains("company LAND_LOST " + job.id()));
        assertEquals(List.of(), penalties);
    }

    @Test
    void aTakenJobWhoseLandChangedHandsAfterItWasRetakenIsLeftAlone() {
        var job = posted(200);
        service.take(company, manager, job.id());
        var read = jobs.job(job.id()).orElseThrow();
        service.giveBack(company, manager, job.id());
        jobs.take(job.id(), company.id() + 100, read.taken(), read.due());

        assertTrue(!service.landLost(read, null));
        assertEquals(JobState.TAKEN, jobs.job(job.id()).orElseThrow().state());
    }

    @Test
    void takenJobsUseTheSlotsOfTheCompany() {
        slots = 1;
        var first = posted(100);
        var second = ((JobService.Posted.Done) post(100, 24, new JobArea("world", 20, 60, 20, 29, 64, 29))).job();

        assertEquals(JobService.Change.DONE, service.take(company, manager, first.id()));
        assertEquals(JobService.Change.LIMIT, service.take(company, manager, second.id()));
    }

    @Test
    void anAreaOverTheChunkLimitAndTooManyWaitingJobsAreRefused() {
        settings.maxChunks(1);
        assertEquals(JobService.Refusal.CHUNKS, refusal(post(100, 24, new JobArea("world", 0, 60, 0, 40, 60, 0))));
        settings.maxChunks(64);
        settings.maxOpenPerOwner(1);
        posted(100);

        assertEquals(JobService.Refusal.TOO_MANY, refusal(post(100, 24, new JobArea("world", 40, 60, 40, 41, 61, 41))));
    }

    @Test
    void theJobsOfADisbandedCompanyAreOpenAgainWithoutAPenalty() {
        var job = posted(200);
        service.take(company, manager, job.id());

        assertEquals(List.of(job.id()), service.releaseAll(company.id()));
        assertEquals(JobState.OPEN, jobs.job(job.id()).orElseThrow().state());
        assertEquals(List.of(), penalties);
    }

    @Test
    void onlyAManagerOfTheWorkingCompanyMayFinish() {
        var job = posted(200);

        assertEquals(JobService.Change.NOT_YOURS, service.mayFinish(company, manager, job.id()));
        service.take(company, manager, job.id());
        assertEquals(JobService.Change.NO_PERMISSION, service.mayFinish(company, worker, job.id()));
        assertEquals(JobService.Change.DONE, service.mayFinish(company, manager, job.id()));
    }

    @Test
    void workOfACompanyThatGaveTheJobBackIsNotCountedForTheNext() {
        var job = posted(200);
        service.take(company, manager, job.id());
        service.giveBack(company, manager, job.id());

        jobs.work(job.id(), company.id(), worker, 50);

        assertEquals(Map.of(), jobs.workOf(job.id()));
    }

    @Test
    void aTerraformJobNeedsATargetInsideItsAreaAndItsOwnVolumeLimit() {
        settings.digMaxVolume(1);

        assertEquals(JobService.Refusal.TARGET, refusal(terraform(null)));
        assertEquals(JobService.Refusal.TARGET, refusal(terraform(new JobTarget(70, null))));
        assertEquals(
                JobService.Refusal.TARGET,
                refusal(service.post(
                        owner,
                        JobKind.DIG,
                        "Pit",
                        100,
                        24,
                        NODE,
                        AREA,
                        JobLand.REGION,
                        "home",
                        JobService.Details.shape(new JobTarget(62, null)))));
        var posted = assertInstanceOf(JobService.Posted.Done.class, terraform(new JobTarget(62, "GRASS_BLOCK")));
        assertEquals(
                new JobTarget(62, "GRASS_BLOCK"),
                jobs.job(posted.job().id()).orElseThrow().target());
        settings.terraformMaxVolume(10);
        assertEquals(JobService.Refusal.VOLUME, refusal(terraform(new JobTarget(62, null))));
    }

    private JobService.Posted terraform(@Nullable JobTarget target) {
        return service.post(
                owner,
                JobKind.TERRAFORM,
                "Flat",
                100,
                24,
                NODE,
                AREA,
                JobLand.REGION,
                "home",
                new JobService.Details(target, null, null, null));
    }

    @Test
    void aBuildJobNeedsABlueprintAndStoresIt() {
        var blueprint = Blueprint.of(List.of(new Blueprint.Cell(0, 0, 0, Material.STONE)));

        assertEquals(
                JobService.Refusal.BLUEPRINT,
                refusal(service.post(owner, JobKind.BUILD, "Hut", 100, 24, NODE, AREA, JobLand.REGION, "home")));
        assertEquals(
                JobService.Refusal.BLUEPRINT,
                refusal(service.post(
                        owner,
                        JobKind.DIG,
                        "Pit",
                        100,
                        24,
                        NODE,
                        AREA,
                        JobLand.REGION,
                        "home",
                        JobService.Details.build(blueprint))));
        var posted = assertInstanceOf(
                JobService.Posted.Done.class,
                service.post(
                        owner,
                        JobKind.BUILD,
                        "Hut",
                        100,
                        24,
                        NODE,
                        AREA,
                        JobLand.REGION,
                        "home",
                        JobService.Details.build(blueprint)));

        assertEquals(
                1,
                Blueprint.decode(jobs.blueprint(posted.job().id()).orElseThrow())
                        .blocks());
    }

    @Test
    void blueprintsAreReadAgainAfterAFailureAndForgottenWhenTheirJobEnds() {
        var job = posted(100);
        var blueprints = new Blueprints(jobs, Logger.getAnonymousLogger());
        jobs.blueprint(job.id(), "not a blueprint");

        assertTrue(blueprints.of(job.id()).isEmpty());
        var built = assertInstanceOf(
                        JobService.Posted.Done.class,
                        service.post(
                                owner,
                                JobKind.BUILD,
                                "Hut",
                                100,
                                24,
                                NODE,
                                new JobArea("world", 20, 60, 20, 21, 61, 21),
                                JobLand.REGION,
                                "home",
                                JobService.Details.build(
                                        Blueprint.of(List.of(new Blueprint.Cell(0, 0, 0, Material.STONE))))))
                .job();
        assertTrue(blueprints.of(built.id()).isPresent());
        assertTrue(blueprints.known(built.id()).isPresent());
        blueprints.prune(id -> false);
        assertTrue(blueprints.known(built.id()).isEmpty());
    }

    @Test
    void aHarvestJobNeedsAnItemPriceWithinItsBudget() {
        assertEquals(JobService.Refusal.UNIT_PRICE, refusal(harvest(100, null)));
        assertEquals(JobService.Refusal.UNIT_PRICE, refusal(harvest(100, 0.0)));
        assertEquals(JobService.Refusal.UNIT_PRICE, refusal(harvest(100, 101.0)));
        assertEquals(
                JobService.Refusal.UNIT_PRICE,
                refusal(service.post(
                        owner,
                        JobKind.DIG,
                        "Pit",
                        100,
                        24,
                        NODE,
                        AREA,
                        JobLand.REGION,
                        "home",
                        JobService.Details.harvest(1.0))));
        assertEquals(1000.0, balances.get(owner), 0.001);
    }

    @Test
    void harvestedItemsArePaidFromTheBudgetUntilItIsUsedUp() {
        var job = harvested(10, 1);
        service.take(company, manager, job.id());

        assertTrue(service.harvested(job.id(), company.id(), worker, Map.of("WHEAT", 4)) > 0);
        assertEquals(4.0, jobs.job(job.id()).orElseThrow().harvest().paid(), 0.001);
        assertTrue(service.harvested(job.id(), company.id(), worker, Map.of("WHEAT", 10)) > 0);

        var ended = jobs.job(job.id()).orElseThrow();
        assertEquals(JobState.PAID, ended.state());
        assertEquals(JobService.BUDGET_BY, ended.closedBy());
        assertEquals(10.0, ended.harvest().paid(), 0.001);
        assertEquals(Map.of("WHEAT", 14), jobs.stock(job.id()));
        assertTrue(notices.contains("owner HARVEST_ENDED " + job.id()));
        assertEquals(0.0, service.harvested(job.id(), company.id(), worker, Map.of("WHEAT", 2)), 0.001);
        assertEquals(Map.of("WHEAT", 16), jobs.stock(job.id()));
    }

    @Test
    void aHarvestWhoseTimeRunsOutEndsWithoutPenaltyAndGivesTheRestBack() {
        var job = harvested(10, 1);
        service.take(company, manager, job.id());
        service.harvested(job.id(), company.id(), worker, Map.of("WHEAT", 4));
        var before = balances.get(owner);
        clock = Clock.offset(clock, Duration.ofHours(25));
        service();

        var check = service.check();

        assertEquals(List.of(job.id()), check.ended());
        assertEquals(List.of(), check.reopened());
        assertEquals(List.of(), penalties);
        assertEquals(before + 6, balances.get(owner), 0.001);
        assertEquals(
                JobService.HARVEST_TIME_BY, jobs.job(job.id()).orElseThrow().closedBy());
    }

    @Test
    void eitherSideMayEndAHarvestButNotApproveOrDisputeIt() {
        var job = harvested(10, 1);

        assertEquals(JobService.Change.TAKEN, service.endAsOwner(owner, "Owner", job.id()));
        service.take(company, manager, job.id());
        assertEquals(JobService.Change.HARVEST, service.approve(owner, "Owner", job.id()));
        assertEquals(JobService.Change.HARVEST, service.disputeAsOwner(owner, job.id(), "Slow"));
        assertEquals(JobService.Change.HARVEST, service.mayFinish(company, manager, job.id()));
        assertEquals(JobService.Change.NO_PERMISSION, service.endAsCompany(company, worker, job.id()));
        assertEquals(JobService.Change.DONE, service.endAsCompany(company, manager, job.id()));
        assertEquals(JobState.PAID, jobs.job(job.id()).orElseThrow().state());
        assertEquals(JobService.Change.CLOSED, service.endAsOwner(owner, "Owner", job.id()));
    }

    @Test
    void aPartPaidHarvestGivenBackRefundsOnlyWhatIsLeftOfTheBudget() {
        var job = harvested(10, 1);
        service.take(company, manager, job.id());
        service.harvested(job.id(), company.id(), worker, Map.of("WHEAT", 4));
        service.giveBack(company, manager, job.id());
        var before = balances.get(owner);

        assertEquals(JobService.Change.DONE, service.cancel(owner, "Owner", job.id()));

        assertEquals(before + 6, balances.get(owner), 0.001);
    }

    @Test
    void aHarvestForACompanyWithoutTheJobGoesIntoTheStockUnpaid() {
        var job = harvested(10, 1);

        assertEquals(0.0, service.harvested(job.id(), company.id(), worker, Map.of("WHEAT", 3)), 0.001);

        assertEquals(Map.of("WHEAT", 3), jobs.stock(job.id()));
        assertEquals(0.0, jobs.job(job.id()).orElseThrow().harvest().paid(), 0.001);
        assertEquals(null, balances.get(worker));
    }

    @Test
    void aHarvestWhoseLandChangedHandsEndsAndGivesTheRestBack() {
        var job = harvested(10, 1);
        service.take(company, manager, job.id());
        service.harvested(job.id(), company.id(), worker, Map.of("WHEAT", 4));
        var before = balances.get(owner);

        assertTrue(service.landLost(jobs.job(job.id()).orElseThrow(), null));

        assertEquals(JobState.CANCELLED, jobs.job(job.id()).orElseThrow().state());
        assertEquals(before + 6, balances.get(owner), 0.001);
    }

    private Job harvested(double budget, double unit) {
        return ((JobService.Posted.Done) harvest(budget, unit)).job();
    }

    private JobService.Posted harvest(double budget, @Nullable Double unit) {
        return service.post(
                owner,
                JobKind.HARVEST,
                "Wheat",
                budget,
                24,
                NODE,
                AREA,
                JobLand.REGION,
                "home",
                new JobService.Details(null, unit, null, null));
    }

    @Test
    void aContractNeedsNoAreaChecksAndOverlapsNothing() {
        settings.digMaxVolume(1);

        var first = contract(200);
        var second = contract(100);

        assertEquals(JobKind.CONTRACT, first.kind());
        assertEquals(JobKind.CONTRACT, second.kind());
        assertEquals(JobService.Change.CONTRACT, mayFinishAfterTaking(first));
    }

    @Test
    void aContractTheCompanySaysIsDoneIsPaidWhenTheOwnerApproves() {
        var job = contract(200);
        service.take(company, manager, job.id());

        assertEquals(JobService.Change.NOT_DONE, service.reject(owner, job.id(), "No"));
        assertEquals(JobService.Change.NO_PERMISSION, service.markDone(company, worker, job.id()));
        assertEquals(JobService.Change.DONE, service.markDone(company, manager, job.id()));
        assertEquals(JobState.DONE, jobs.job(job.id()).orElseThrow().state());
        assertEquals(JobService.Change.UNKNOWN, service.approve(manager, "Manager", job.id()));
        assertEquals(JobService.Change.DONE, service.approve(owner, "Owner", job.id()));

        assertEquals(JobState.PAID, jobs.job(job.id()).orElseThrow().state());
        assertTrue(balances.getOrDefault(manager, 0.0) > 0);
        assertTrue(notices.contains("owner AWAITING " + job.id()));
        assertTrue(notices.contains("company PAID " + job.id()));
    }

    @Test
    void aDigJobIsDoneByItsCheckNotBySayingSo() {
        var job = posted(200);
        service.take(company, manager, job.id());

        assertEquals(JobService.Change.AREA, service.markDone(company, manager, job.id()));
    }

    @Test
    void anOwnerMayApproveATakenJobEarlyAndItsWorkersArePaidAsItStands() {
        var job = posted(200);
        service.take(company, manager, job.id());
        jobs.work(job.id(), company.id(), worker, 30);

        assertEquals(JobService.Change.DONE, service.approve(owner, "Owner", job.id()));

        assertEquals(JobState.PAID, jobs.job(job.id()).orElseThrow().state());
        assertTrue(balances.getOrDefault(worker, 0.0) > 0);
        assertEquals(790.0, balances.get(owner), 0.001);
    }

    @Test
    void aRejectionGivesTheCompanyBackTheTimeItWaited() {
        var job = contract(200);
        service.take(company, manager, job.id());
        service.markDone(company, manager, job.id());
        clock = Clock.offset(clock, Duration.ofHours(10));
        service();

        assertEquals(JobService.Change.REASON, service.reject(owner, job.id(), " "));
        assertEquals(JobService.Change.DONE, service.reject(owner, job.id(), "Not yet"));

        var rejected = jobs.job(job.id()).orElseThrow();
        assertEquals(JobState.TAKEN, rejected.state());
        assertEquals(local(NOW).plusHours(34), rejected.due());
        assertEquals("Not yet", rejected.review().rejected());
        assertTrue(notices.contains("company REJECTED " + job.id() + " Not yet"));
    }

    @Test
    void aJobTheOwnerDoesNotAnswerIsApprovedAfterTheirTime() {
        var job = contract(200);
        service.take(company, manager, job.id());
        service.markDone(company, manager, job.id());
        clock = Clock.offset(clock, Duration.ofHours(71));
        service();
        assertEquals(List.of(), service.check().approved());
        clock = Clock.offset(clock, Duration.ofHours(1));
        service();

        assertEquals(List.of(job.id()), service.check().approved());

        var paid = jobs.job(job.id()).orElseThrow();
        assertEquals(JobState.PAID, paid.state());
        assertEquals(JobService.AUTO_APPROVED_BY, paid.closedBy());
        assertTrue(notices.contains("owner AUTO_APPROVED " + job.id()));
    }

    @Test
    void aDisputeFreezesTheJobUntilAnAdminSplitsThePrice() {
        var job = contract(200);
        service.take(company, manager, job.id());

        assertEquals(JobService.Change.NO_PERMISSION, service.disputeAsCompany(company, worker, job.id(), "Unfair"));
        assertEquals(JobService.Change.DONE, service.disputeAsOwner(owner, job.id(), "Nothing happened"));
        assertEquals(JobService.Change.DISPUTED, service.disputeAsOwner(owner, job.id(), "Again"));
        assertEquals(JobService.Change.DISPUTED, service.giveBack(company, manager, job.id()));
        assertTrue(notices.contains("company DISPUTED " + job.id() + " Nothing happened"));
        assertFalse(notices.contains("owner DISPUTED " + job.id()));
        assertEquals(JobService.Change.TAKEN, service.cancel(owner, "Owner", job.id()));
        clock = Clock.offset(clock, Duration.ofDays(30));
        service();
        assertEquals(List.of(), service.check().reopened());

        assertEquals(JobService.Change.DONE, service.settle("Admin", job.id(), 60));

        var settled = jobs.job(job.id()).orElseThrow();
        assertEquals(JobState.SETTLED, settled.state());
        assertEquals(60, settled.review().settled());
        assertEquals(870.0, balances.get(owner), 0.001);
        assertEquals(JobService.Change.CLOSED, service.settle("Admin", job.id(), 60));
        assertTrue(notices.contains("owner SETTLED " + job.id()));
        assertTrue(notices.contains("company SETTLED " + job.id()));
    }

    @Test
    void aDisputeSettledForTheOwnerGivesThePriceBackAndKeepsTheFee() {
        var job = contract(200);
        service.take(company, manager, job.id());
        service.disputeAsCompany(company, manager, job.id(), "Owner blocks us");

        assertEquals(JobService.Change.DONE, service.settle("Admin", job.id(), 0));

        assertEquals(990.0, balances.get(owner), 0.001);
        assertEquals(List.of(10.0), collected);
    }

    @Test
    void anOwnerMayStillApproveADisputedJob() {
        var job = contract(200);
        service.take(company, manager, job.id());
        service.disputeAsCompany(company, manager, job.id(), "Owner blocks us");

        assertEquals(JobService.Change.DONE, service.approve(owner, "Owner", job.id()));

        assertEquals(JobState.PAID, jobs.job(job.id()).orElseThrow().state());
        assertTrue(notices.contains("owner DISPUTED " + job.id()));
        assertFalse(notices.stream().anyMatch(notice -> notice.startsWith("company DISPUTED")));
    }

    @Test
    void onlyTheSideThatOpenedADisputeWithdrawsItAndTheCompanyGetsTheTimeBack() {
        var job = contract(200);
        service.take(company, manager, job.id());
        service.disputeAsOwner(owner, job.id(), "Nothing happened");
        clock = Clock.offset(clock, Duration.ofHours(5));
        service();

        assertEquals(JobService.Change.NOT_YOURS, service.withdrawAsCompany(company, manager, job.id()));
        assertEquals(JobService.Change.DONE, service.withdrawAsOwner(owner, job.id()));

        var taken = jobs.job(job.id()).orElseThrow();
        assertEquals(JobState.TAKEN, taken.state());
        assertEquals(local(NOW).plusHours(29), taken.due());
        assertTrue(notices.contains("company WITHDRAWN " + job.id()));
        assertEquals(JobService.Change.CLOSED, service.withdrawAsOwner(owner, job.id()));
    }

    @Test
    void aJobTheCompanyDisputesKeepsItsSlotAndOneTheOwnerDisputesFreesIt() {
        var first = contract(200);
        var second = contract(100);
        service.take(company, manager, first.id());
        service.take(company, manager, second.id());
        var orders = OrderTable.create().orderData();
        assertEquals(2, orders.retrieveCompanySlotCount(company).join());

        service.disputeAsCompany(company, manager, first.id(), "Owner blocks us");
        service.disputeAsOwner(owner, second.id(), "Nothing happened");

        assertEquals(1, orders.retrieveCompanySlotCount(company).join());
    }

    @Test
    void anOwnerWithdrawingADisputeOnFinishedWorkKeepsTheApprovalClockRunning() {
        var job = contract(200);
        service.take(company, manager, job.id());
        service.markDone(company, manager, job.id());
        clock = Clock.offset(clock, Duration.ofHours(70));
        service();
        service.disputeAsOwner(owner, job.id(), "Hm");
        service.withdrawAsOwner(owner, job.id());
        clock = Clock.offset(clock, Duration.ofHours(2));
        service();

        assertEquals(List.of(job.id()), service.check().approved());
        assertEquals(List.of(), service.check().reopened());
    }

    @Test
    void aCompanyWithdrawingItsDisputeLeavesTheOwnerTheirTimeToAnswer() {
        var job = contract(200);
        service.take(company, manager, job.id());
        service.markDone(company, manager, job.id());
        service.disputeAsCompany(company, manager, job.id(), "Owner unresponsive");
        clock = Clock.offset(clock, Duration.ofHours(80));
        service();
        service.withdrawAsCompany(company, manager, job.id());

        assertEquals(List.of(), service.check().approved());
        var waiting = jobs.job(job.id()).orElseThrow();
        assertEquals(JobState.DONE, waiting.state());
        assertEquals(local(NOW).plusHours(80), waiting.review().doneAt());
        assertEquals(JobService.Change.DONE, service.reject(owner, job.id(), "Not finished"));
    }

    @Test
    void aSettlementWithAShareForAGoneCompanyIsRefused() {
        var job = contract(200);
        service.take(company, manager, job.id());
        service.disputeAsOwner(owner, job.id(), "Nothing happened");
        gone = true;

        assertEquals(JobService.Change.NO_COMPANY, service.settle("Admin", job.id(), 60));
        assertEquals(JobService.Change.DONE, service.settle("Admin", job.id(), 0));
        assertEquals(990.0, balances.get(owner), 0.001);
    }

    @Test
    void anApprovalReadForOneCompanyNeverPaysAJobAnotherCompanyTookMeanwhile() {
        var job = contract(200);
        service.take(company, manager, job.id());
        var read = jobs.job(job.id()).orElseThrow();
        jobs.release(job.id(), company.id());
        jobs.take(job.id(), company.id() + 1, local(NOW), local(NOW).plusHours(24));

        assertFalse(jobs.closeHeld(read.id(), company.id(), read.state(), JobState.PAID, "Owner", local(NOW)));
        assertFalse(jobs.dispute(read.id(), company.id(), read.state(), owner, "Late", local(NOW)));
        assertEquals(JobState.TAKEN, jobs.job(job.id()).orElseThrow().state());
    }

    @Test
    void aJobIsDescribedOnlyWhileItWaits() {
        var job = contract(200);

        assertEquals(JobService.Change.DONE, service.describe(owner, job.id(), "Paint the barn red"));
        assertEquals("Paint the barn red", jobs.job(job.id()).orElseThrow().description());
        assertEquals(
                JobService.Change.TOO_LONG, service.describe(owner, job.id(), "x".repeat(Job.MAX_DESCRIPTION + 1)));
        assertEquals(JobService.Change.UNKNOWN, service.describe(manager, job.id(), "Mine"));
        service.take(company, manager, job.id());
        assertEquals(JobService.Change.TAKEN, service.describe(owner, job.id(), "Blue"));
    }

    @Test
    void aShareIsRoundedDownToTheCent() {
        assertEquals(66.66, JobService.share(111.11, 60), 0.0001);
        assertEquals(0.0, JobService.share(200, 0), 0.0001);
        assertEquals(200.0, JobService.share(200, 100), 0.0001);
    }

    private JobService.Change mayFinishAfterTaking(Job job) {
        service.take(company, manager, job.id());
        return service.mayFinish(company, manager, job.id());
    }

    private Job contract(double price) {
        return ((JobService.Posted.Done)
                        service.post(owner, JobKind.CONTRACT, "Paint", price, 24, NODE, POINT, JobLand.NONE, null))
                .job();
    }

    private static LocalDateTime local(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC).toLocalDateTime();
    }

    private Job posted(double price) {
        return ((JobService.Posted.Done) post(price, 24, AREA)).job();
    }

    private JobService.Posted post(double price, int hours, JobArea area) {
        return service.post(owner, JobKind.DIG, "Cellar", price, hours, NODE, area, JobLand.REGION, "home");
    }

    private static JobService.Refusal refusal(JobService.Posted posted) {
        return assertInstanceOf(JobService.Posted.Refused.class, posted).refusal();
    }

    private void service() {
        var money = new JobService.Money() {
            @Override
            public boolean withdraw(UUID player, double amount) {
                var balance = balances.getOrDefault(player, 0.0);
                if (balance < amount) return false;
                balances.put(player, balance - amount);
                return true;
            }

            @Override
            public void deposit(UUID player, double amount) {
                balances.merge(player, amount, Double::sum);
            }

            @Override
            public void collect(double amount) {
                collected.add(amount);
            }

            @Override
            public double taxPercent(CompanyProfile company) {
                return 10;
            }

            @Override
            public int share(CompanyProfile company) {
                return 20;
            }

            @Override
            public void keep(CompanyProfile company, int job, long cents) {
                kept.add(cents);
            }
        };
        var noticed = new JobService.Notices() {
            @Override
            public void owner(UUID owner, JobService.Notice notice, Job job, @Nullable Object detail) {
                notices.add("owner " + notice + " " + job.id());
            }

            @Override
            public void paid(CompanyProfile company, Job job, double paid) {
                notices.add("company PAID " + job.id());
            }

            @Override
            public void overdue(int company, Job job) {
                notices.add("overdue " + job.id());
            }

            @Override
            public void landLost(int company, Job job) {
                notices.add("company LAND_LOST " + job.id());
            }

            @Override
            public void rejected(int company, Job job, String reason) {
                notices.add("company REJECTED " + job.id() + " " + reason);
            }

            @Override
            public void disputed(int company, Job job, String reason) {
                notices.add("company DISPUTED " + job.id() + " " + reason);
            }

            @Override
            public void settled(int company, Job job, double paid) {
                notices.add("company SETTLED " + job.id());
            }

            @Override
            public void withdrawn(int company, Job job) {
                notices.add("company WITHDRAWN " + job.id());
            }

            @Override
            public void extended(int company, Job job, int hours, LocalDateTime due) {
                notices.add("company EXTENDED " + job.id() + " " + hours);
            }

            @Override
            public void extensionRefused(int company, Job job, @Nullable String reason) {
                notices.add("company REFUSED " + job.id() + " " + reason);
            }
        };
        service = new JobService(
                jobs,
                OrderTable.create().orderData(),
                money,
                noticed,
                (company, overdue) -> penalties.add(company + (overdue ? " overdue" : " gave back")),
                id -> id == company.id() && !gone ? Optional.of(company) : Optional.empty(),
                () -> settings,
                price -> new OrderFee(10, 0, 0, 0).of(price),
                found -> slots,
                clock,
                Logger.getAnonymousLogger());
    }
}
