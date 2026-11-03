/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.components.job.JobState;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteJobData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobLand;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The materials of a supplied build job: what is missing, handing plain items in up to what is missing with empty
 * buckets back, and taking them from the owner's item returns, only while the job is a supplied draft.
 */
class SuppliesTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 12, 0);

    @TempDir
    Path directory;

    private final UUID owner = UUID.randomUUID();
    private ExecutorService executor;
    private SqLiteJobData jobs;
    private Supplies supplies;
    private int job;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(2);
        jobs = new SqLiteJobData();
        var blueprint = Blueprint.of(List.of(
                new Blueprint.Cell(0, 0, 0, Material.STONE),
                new Blueprint.Cell(1, 0, 0, Material.STONE),
                new Blueprint.Cell(2, 0, 0, Material.STONE),
                new Blueprint.Cell(0, 1, 0, Material.WATER, "minecraft:water[level=0]")));
        var area = new JobArea("world", 0, 64, 0, 2, 65, 0);
        job = jobs.create(Job.draft(
                                owner,
                                JobKind.BUILD,
                                "Wall",
                                100,
                                0,
                                UUID.randomUUID(),
                                area,
                                JobLand.NONE,
                                null,
                                24,
                                NOW,
                                NOW)
                        .withState(JobState.DRAFT)
                        .withSupplied(true))
                .orElseThrow();
        jobs.blueprint(job, blueprint.encode());
        supplies = new Supplies(jobs, new Blueprints(jobs, Logger.getAnonymousLogger()));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        MockBukkit.unmock();
    }

    @Test
    void whatIsMissingIsWhatTheBlueprintTakesLessTheStock() {
        assertEquals(Map.of(Material.STONE, 3, Material.WATER_BUCKET, 2), supplies.missing(job));

        jobs.stock(job, Map.of("STONE", 2));

        assertEquals(Map.of(Material.STONE, 1, Material.WATER_BUCKET, 2), supplies.missing(job));
    }

    @Test
    void onlyPlainItemsUpToWhatIsMissingGoInAndBucketsComeBackEmpty() {
        var named = new ItemStack(Material.STONE, 5);
        named.editMeta(meta -> meta.displayName(Component.text("Shiny")));

        var handed = supplies.handIn(
                job,
                List.of(
                        new ItemStack(Material.STONE, 5),
                        named,
                        new ItemStack(Material.WATER_BUCKET),
                        new ItemStack(Material.DIRT, 4)));

        assertEquals(Map.of(Material.STONE, 3, Material.WATER_BUCKET, 1), handed.taken());
        assertTrue(handed.back().contains(new ItemStack(Material.STONE, 2)));
        assertTrue(handed.back().contains(named));
        assertTrue(handed.back().contains(new ItemStack(Material.BUCKET, 1)));
        assertTrue(handed.back().contains(new ItemStack(Material.DIRT, 4)));
        assertEquals(Map.of(Material.WATER_BUCKET, 1), supplies.missing(job));
    }

    @Test
    void theItemReturnsSupplyWhatIsMissing() {
        var orders = new SqLiteOrderData(executor, SqLiteTestDatabase.mapper());
        orders.submitItemReturn(owner, ReturnKind.OWNER, new ItemStack(Material.STONE), 10)
                .asFuture()
                .join();
        orders.submitItemReturn(owner, ReturnKind.WORKER, new ItemStack(Material.WATER_BUCKET), 2)
                .asFuture()
                .join();

        var taken = supplies.fromReturns(owner, job, orders);

        assertEquals(Map.of(Material.STONE, 3, Material.WATER_BUCKET, 2), taken);
        assertEquals(Map.of(), supplies.missing(job));
        var left =
                orders.retrieveItemReturns(owner, ReturnKind.OWNER).asFuture().join();
        assertTrue(left.stream().anyMatch(entry -> entry.stack().getType() == Material.STONE && entry.amount() == 7));
        assertTrue(left.stream().anyMatch(entry -> entry.stack().getType() == Material.BUCKET && entry.amount() == 2));
    }

    @Test
    void aJobThatIsNoDraftAnyMoreTakesNothingAndEverythingGoesBack() {
        jobs.deleteDraft(job);
        var items = List.of(new ItemStack(Material.STONE, 5), new ItemStack(Material.WATER_BUCKET));

        var handed = supplies.handIn(job, items);

        assertFalse(handed.stored());
        assertEquals(Map.of(), handed.taken());
        assertEquals(items, handed.back());
    }

    @Test
    void theItemReturnsKeepWhatADeletedDraftNoLongerTakesInTheirKind() {
        var orders = new SqLiteOrderData(executor, SqLiteTestDatabase.mapper());
        orders.submitItemReturn(owner, ReturnKind.WORKER, new ItemStack(Material.STONE), 10)
                .asFuture()
                .join();
        supplies.missing(job);
        jobs.deleteDraft(job);

        var taken = supplies.fromReturns(owner, job, orders);

        assertEquals(Map.of(), taken);
        var left =
                orders.retrieveItemReturns(owner, ReturnKind.WORKER).asFuture().join();
        assertEquals(
                10,
                left.stream()
                        .filter(entry -> entry.stack().getType() == Material.STONE)
                        .mapToInt(entry -> entry.amount())
                        .sum());
    }

    @Test
    void handInsNeverFillTheStockBeyondWhatIsNeeded() {
        supplies.handIn(job, List.of(new ItemStack(Material.STONE, 2)));
        jobs.stock(job, Map.of("STONE", 1));

        var handed = supplies.handIn(job, List.of(new ItemStack(Material.STONE, 4)));

        assertEquals(Map.of(), handed.taken());
        assertEquals(List.of(new ItemStack(Material.STONE, 4)), handed.back());
        assertEquals(Map.of("STONE", 3), jobs.stock(job));
    }
}
