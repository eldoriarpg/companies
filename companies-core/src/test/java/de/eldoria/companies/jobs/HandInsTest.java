/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.components.job.JobState;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteJobData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobLand;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Closing a hand-in: what the job does not take comes back into the inventory of an online player, into the item
 * returns of one who left, and everything comes back when the job is no supplied draft any more. Shift-clicked items
 * fill partial stacks first.
 */
class HandInsTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 12, 0);
    private static final long WAIT_MILLIS = 5000;

    @TempDir
    Path directory;

    private ServerMock server;
    private ExecutorService executor;
    private SqLiteJobData jobs;
    private SqLiteOrderData orders;
    private HandIns handIns;
    private InventoryHandIn inventory;
    private Supplies supplies;
    private final List<@Nullable Map<Material, Integer>> told = new ArrayList<>();
    private int job;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        var messages = StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(2);
        jobs = new SqLiteJobData();
        orders = new SqLiteOrderData(executor, SqLiteTestDatabase.mapper());
        var area = new JobArea("world", 0, 64, 0, 1, 64, 0);
        job = jobs.create(Job.draft(
                                UUID.randomUUID(),
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
        jobs.blueprint(
                job,
                Blueprint.of(List.of(
                                new Blueprint.Cell(0, 0, 0, Material.STONE),
                                new Blueprint.Cell(1, 0, 0, Material.STONE)))
                        .encode());
        supplies = new Supplies(jobs, new Blueprints(jobs, Logger.getAnonymousLogger()));
        var returns = new ItemReturnService(plugin, orders);
        inventory = new InventoryHandIn(plugin, () -> supplies);
        inventory.returns(() -> returns);
        handIns = new HandIns(
                plugin, () -> supplies, () -> inventory, messages, (player, id, missing) -> told.add(missing));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        MockBukkit.unmock();
    }

    @Test
    void anOnlinePlayerGetsBackWhatTheJobDoesNotTake() {
        var player = server.addPlayer();

        await(handIns.close(player, job, List.of(new ItemStack(Material.STONE, 5), new ItemStack(Material.DIRT, 3))));

        assertEquals(Map.of("STONE", 2), jobs.stock(job));
        assertTrue(player.getInventory().containsAtLeast(new ItemStack(Material.STONE), 3));
        assertTrue(player.getInventory().containsAtLeast(new ItemStack(Material.DIRT), 3));
        assertEquals(List.of(Map.of()), told);
    }

    @Test
    void aPlayerWhoLeftFindsWhatComesBackInTheItemReturns() throws InterruptedException {
        var player = server.addPlayer();
        player.disconnect();

        await(handIns.close(player, job, List.of(new ItemStack(Material.STONE, 5))));

        assertEquals(Map.of("STONE", 2), jobs.stock(job));
        assertEquals(3, returned(player.getUniqueId(), Material.STONE, 3));
        assertTrue(told.isEmpty());
    }

    @Test
    void aJobThatIsNoSuppliedDraftAnyMoreGivesEverythingBack() {
        var player = server.addPlayer();
        jobs.deleteDraft(job);

        await(handIns.close(player, job, List.of(new ItemStack(Material.STONE, 5))));

        assertTrue(player.getInventory().containsAtLeast(new ItemStack(Material.STONE), 5));
        assertEquals(1, told.size());
        assertNull(told.getFirst());
    }

    @Test
    void takingFromTheInventoryTakesOnlyThePlainItemsTheDraftLacks() {
        var player = server.addPlayer();
        var named = new ItemStack(Material.STONE, 4);
        named.editMeta(meta -> meta.displayName(Component.text("Shiny")));
        player.getInventory().setItem(0, named);
        player.getInventory().setItem(1, new ItemStack(Material.STONE, 5));
        player.getInventory().setItem(2, new ItemStack(Material.DIRT, 3));

        var handed = await(inventory.take(player.getUniqueId(), job));

        assertTrue(handed.online());
        assertEquals(Map.of(Material.STONE, 2), handed.taken());
        assertEquals(Map.of(), handed.missing());
        assertEquals(Map.of("STONE", 2), jobs.stock(job));
        assertEquals(named, player.getInventory().getItem(0));
        assertEquals(new ItemStack(Material.STONE, 3), player.getInventory().getItem(1));
        assertEquals(new ItemStack(Material.DIRT, 3), player.getInventory().getItem(2));
    }

    @Test
    void takingFromTheInventoryOfAPlayerNotOnlineTakesNothing() {
        var handed = await(inventory.take(UUID.randomUUID(), job));

        assertFalse(handed.online());
        assertEquals(Map.of(), jobs.stock(job));
    }

    @Test
    void takingFromTheInventoryLeavesArmourAndOffhandAlone() {
        var player = server.addPlayer();
        player.getInventory().setItem(36, new ItemStack(Material.STONE, 5));
        player.getInventory().setItemInOffHand(new ItemStack(Material.STONE, 5));

        var handed = await(inventory.take(player.getUniqueId(), job));

        assertTrue(handed.taken().isEmpty());
        assertEquals(new ItemStack(Material.STONE, 5), player.getInventory().getItem(36));
        assertEquals(new ItemStack(Material.STONE, 5), player.getInventory().getItemInOffHand());
        assertEquals(Map.of(), jobs.stock(job));
    }

    @Test
    void aDraftDeletedWhileTakingGivesEverythingBack() {
        var player = server.addPlayer();
        player.getInventory().setItem(0, new ItemStack(Material.STONE, 5));
        supplies.missing(job);
        jobs.deleteDraft(job);

        var handed = await(inventory.take(player.getUniqueId(), job));

        assertFalse(handed.stored());
        assertTrue(player.getInventory().containsAtLeast(new ItemStack(Material.STONE), 5));
    }

    @Test
    void aFullBucketTakenComesBackEmpty() {
        var player = server.addPlayer();
        var area = new JobArea("world", 10, 64, 0, 10, 64, 0);
        var pond = jobs.create(Job.draft(
                                UUID.randomUUID(),
                                JobKind.BUILD,
                                "Pond",
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
        jobs.blueprint(
                pond,
                Blueprint.of(List.of(
                                new Blueprint.Cell(0, 0, 0, Material.WATER, "minecraft:water[level=0]"),
                                new Blueprint.Cell(1, 0, 0, Material.STONE)))
                        .encode());
        player.getInventory().setItem(0, new ItemStack(Material.WATER_BUCKET));

        var handed = await(inventory.take(player.getUniqueId(), pond));

        assertEquals(Map.of(Material.WATER_BUCKET, 1), handed.taken());
        assertEquals(Map.of("WATER_BUCKET", 1), jobs.stock(pond));
        assertTrue(player.getInventory().contains(Material.BUCKET));
    }

    private <T> T await(CompletableFuture<T> future) {
        for (var tick = 0; tick < 500 && !future.isDone(); tick++) {
            server.getScheduler().performOneTick();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return future.orTimeout(1, TimeUnit.SECONDS).join();
    }

    @Test
    void shiftClickedItemsFillPartialStacksBeforeEmptySlots() {
        var top = server.createInventory(null, 54);
        top.setItem(HandIns.SHOWN + 3, new ItemStack(Material.STONE, 60));

        var left = HandIns.moveIn(top, new ItemStack(Material.STONE, 10));

        assertNull(left);
        assertEquals(64, top.getItem(HandIns.SHOWN + 3).getAmount());
        assertEquals(new ItemStack(Material.STONE, 6), top.getItem(HandIns.SHOWN));
    }

    private int returned(UUID player, Material type, int expected) throws InterruptedException {
        var deadline = System.currentTimeMillis() + WAIT_MILLIS;
        var amount = 0;
        while (System.currentTimeMillis() < deadline) {
            amount = orders.retrieveItemReturns(player, ReturnKind.OWNER).asFuture().join().stream()
                    .filter(entry -> entry.stack().getType() == type)
                    .mapToInt(entry -> entry.amount())
                    .sum();
            if (amount >= expected) return amount;
            Thread.sleep(20);
        }
        return amount;
    }
}
