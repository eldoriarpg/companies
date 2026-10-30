/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.configuration.elements.jobs.JobSettings;
import de.eldoria.companies.configuration.elements.ordersettings.OrderFee;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteCompanyData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteJobData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobKind;
import de.eldoria.companies.data.wrapper.job.JobLand;
import de.eldoria.companies.data.wrapper.job.JobState;
import de.eldoria.companies.orders.OrderTable;
import de.eldoria.companies.util.TestExecutors;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
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
        };
        service = new JobService(
                jobs,
                OrderTable.create().orderData(),
                money,
                noticed,
                (company, overdue) -> penalties.add(company + (overdue ? " overdue" : " gave back")),
                id -> id == company.id() ? Optional.of(company) : Optional.empty(),
                () -> settings,
                price -> new OrderFee(10, 0, 0, 0).of(price),
                found -> slots,
                clock,
                Logger.getAnonymousLogger());
    }
}
