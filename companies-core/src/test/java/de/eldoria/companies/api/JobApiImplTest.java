/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.api.write.JobProblem;
import de.eldoria.companies.api.write.JobRequest;
import de.eldoria.companies.api.write.PostJobResult;
import de.eldoria.companies.components.job.BuildBlock;
import de.eldoria.companies.components.job.JobBox;
import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.configuration.elements.jobs.JobSettings;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteJobData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.jobs.JobService;
import de.eldoria.companies.jobs.JobServices;
import de.eldoria.companies.jobs.hooks.JobHooks;
import de.eldoria.companies.orders.OrderTable;
import de.eldoria.companies.util.TestExecutors;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Posting and reading jobs through the API: the world and land checks, charging and the request rules of each kind.
 */
class JobApiImplTest {
    private static final UUID NODE = UUID.randomUUID();
    private static final JobBox CELLAR = new JobBox("world", 4, 60, 4, 0, 62, 0);

    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
    private final JobSettings settings = new JobSettings();

    @TempDir
    Path directory;

    private ServerMock server;
    private ExecutorService executor;
    private SqLiteJobData jobs;
    private JobApiImpl api;
    private UUID owner;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(2);
        jobs = new SqLiteJobData();
        owner = UUID.randomUUID();
        balances.put(owner, 1000.0);
        settings.unownedWorlds(List.of("world"));
        var service = new JobService(
                jobs,
                OrderTable.create().orderData(),
                new Money(),
                new Silent(),
                (company, overdue) -> {},
                id -> Optional.empty(),
                () -> settings,
                price -> price / 10,
                company -> 1,
                Clock.systemUTC(),
                Logger.getAnonymousLogger());
        var hooks = new JobHooks(name -> false, className -> new Object(), Logger.getAnonymousLogger());
        var writes = new WriteAccess();
        writes.open(new CompaniesApiImpl.Writes(
                null,
                null,
                null,
                null,
                null,
                null,
                new JobServices(jobs, service, hooks, null, null, NODE, null, null),
                () -> settings,
                Runnable::run));
        api = new JobApiImpl(jobs, writes, server::getOfflinePlayer, executor);
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
        MockBukkit.unmock();
    }

    @Test
    void postsAnUnchargedDigJobAndReadsItBack() throws Exception {
        var result = post(JobRequest.dig(owner, "Cellar", 200, 24, CELLAR).charge(false));

        var job = assertInstanceOf(PostJobResult.Posted.class, result).job();
        assertEquals(JobKind.DIG, job.kind());
        assertEquals(0, job.area().minX());
        assertEquals(4, job.area().maxX());
        assertFalse(job.charged());
        assertEquals(0.0, job.fee());
        assertEquals(1000.0, balances.get(owner));
        assertEquals(
                job.id(),
                api.byId(job.id()).get(5, TimeUnit.SECONDS).orElseThrow().id());
        assertEquals(1, api.open(10).get(5, TimeUnit.SECONDS).size());
        assertEquals(1, api.ofOwner(owner, 10).get(5, TimeUnit.SECONDS).size());
        assertTrue(api.ofCompany(1).get(5, TimeUnit.SECONDS).isEmpty());
    }

    @Test
    void aChargedJobTakesPriceAndFee() throws Exception {
        var job = assertInstanceOf(PostJobResult.Posted.class, post(JobRequest.dig(owner, "Cellar", 200, 24, CELLAR)))
                .job();

        assertTrue(job.charged());
        assertEquals(780.0, balances.get(owner), 0.001);
    }

    @Test
    void namesWhyAJobWasNotPosted() throws Exception {
        assertEquals(
                JobProblem.NO_WORLD,
                problem(JobRequest.dig(owner, "Cellar", 200, 24, new JobBox("nowhere", 0, 60, 0, 1, 61, 1))));
        assertEquals(
                JobProblem.HEIGHT,
                problem(JobRequest.dig(owner, "Cellar", 200, 24, new JobBox("world", 0, -500, 0, 1, 61, 1))));
        assertEquals(JobProblem.TITLE, problem(JobRequest.dig(owner, " ", 200, 24, CELLAR)));
        balances.put(owner, 0.0);
        assertEquals(JobProblem.FUNDS, problem(JobRequest.dig(owner, "Cellar", 200, 24, CELLAR)));
        settings.unownedWorlds(List.of());
        assertEquals(JobProblem.UNOWNED_LAND, problem(JobRequest.dig(owner, "Cellar", 200, 24, CELLAR)));
    }

    @Test
    void aBuildJobLiesWhereItsBlueprintIsPlaced() throws Exception {
        var build = new JobRequest.Build(
                "world",
                10,
                64,
                10,
                List.of(new BuildBlock(0, 0, 0, Material.STONE), new BuildBlock(2, 1, 0, Material.OAK_PLANKS)));

        var job = assertInstanceOf(PostJobResult.Posted.class, post(JobRequest.build(owner, "Hut", 300, 48, build)))
                .job();

        assertEquals(JobKind.BUILD, job.kind());
        assertEquals(10, job.area().minX());
        assertEquals(12, job.area().maxX());
        assertEquals(65, job.area().maxY());
    }

    @Test
    void refusesSurfacesNobodyCanLayAndOversizedBlueprints() throws Exception {
        assertEquals(
                JobProblem.SURFACE,
                problem(JobRequest.terraform(owner, "Floor", 200, 24, CELLAR, 61, Material.BEDROCK)));
        var spread = new JobRequest.Build(
                "world",
                0,
                64,
                0,
                List.of(new BuildBlock(0, 0, 0, Material.STONE), new BuildBlock(3000, 300, 3000, Material.STONE)));
        assertEquals(JobProblem.VOLUME, problem(JobRequest.build(owner, "Tower", 300, 48, spread)));
        assertEquals(
                JobProblem.NO_WORLD,
                problem(JobRequest.contract(owner, "Advice", 50, 12, null, new JobBox("nowhere", 0, 60, 0, 0, 60, 0))));
    }

    @Test
    void aContractNeedsNoWorld() throws Exception {
        var job = assertInstanceOf(
                        PostJobResult.Posted.class,
                        post(JobRequest.contract(owner, "Advice", 50, 12, "Plan my town", null)))
                .job();

        assertEquals(JobKind.CONTRACT, job.kind());
        assertEquals("Plan my town", job.description().orElseThrow());
    }

    @Test
    void requestsCarryWhatTheirKindNeeds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JobRequest(owner, JobKind.DIG, "Cellar", 200, 24, null, null, null, null, null, null, true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JobRequest(
                        owner, JobKind.TERRAFORM, "Flat", 200, 24, CELLAR, null, null, null, null, null, true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JobRequest(owner, JobKind.DIG, "Cellar", 200, 24, CELLAR, 61, null, null, null, null, true));
        assertThrows(IllegalArgumentException.class, () -> api.open(0));
    }

    private PostJobResult post(JobRequest request) throws Exception {
        return api.post(request).get(5, TimeUnit.SECONDS);
    }

    private JobProblem problem(JobRequest request) throws Exception {
        return assertInstanceOf(PostJobResult.Rejected.class, post(request)).problem();
    }

    private final class Money implements JobService.Money {
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
        public void collect(double amount) {}

        @Override
        public double taxPercent(CompanyProfile company) {
            return 0;
        }

        @Override
        public int share(CompanyProfile company) {
            return 0;
        }

        @Override
        public void keep(CompanyProfile company, int job, long cents) {}
    }

    private static final class Silent implements JobService.Notices {
        @Override
        public void owner(UUID owner, JobService.Notice notice, Job job, @Nullable Object detail) {}

        @Override
        public void paid(CompanyProfile company, Job job, double paid) {}

        @Override
        public void overdue(int company, Job job) {}

        @Override
        public void landLost(int company, Job job) {}

        @Override
        public void rejected(int company, Job job, String reason) {}

        @Override
        public void disputed(int company, Job job, String reason) {}

        @Override
        public void settled(int company, Job job, double paid) {}

        @Override
        public void withdrawn(int company, Job job) {}

        @Override
        public void extended(int company, Job job, int hours, LocalDateTime due) {}

        @Override
        public void extensionRefused(int company, Job job, @Nullable String reason) {}
    }
}
