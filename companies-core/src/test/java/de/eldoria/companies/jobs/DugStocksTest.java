/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import com.google.common.util.concurrent.MoreExecutors;
import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.components.job.JobState;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteJobData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobLand;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockDropItemEvent;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The drops of blocks dug for a job whose dug blocks go to its owner: plain items go into the stock, items with data
 * drop as usual, and a drop that does not follow a noted break in the same tick stays where it is.
 */
class DugStocksTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 12, 0);

    @TempDir
    Path directory;

    private ServerMock server;
    private WorldMock world;
    private SqLiteJobData jobs;
    private JobStocks stocks;
    private DugStocks dug;
    private Job job;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        SqLiteTestDatabase.create(directory);
        jobs = new SqLiteJobData();
        var draft = Job.draft(
                UUID.randomUUID(),
                JobKind.DIG,
                "Pit",
                100,
                5,
                UUID.randomUUID(),
                new JobArea("world", 0, 60, 0, 4, 64, 4),
                JobLand.NONE,
                null,
                24,
                NOW,
                NOW.plusDays(7));
        job = draft.withId(jobs.create(draft).orElseThrow());
        stocks = new JobStocks(jobs, Logger.getAnonymousLogger(), MoreExecutors.newDirectExecutorService());
        dug = new DugStocks(server, stocks);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void plainDropsOfADugBlockGoIntoTheStock() {
        var block = world.getBlockAt(1, 60, 1);
        var state = block.getState();
        var named = new ItemStack(Material.SHULKER_BOX);
        named.editMeta(meta -> meta.displayName(Component.text("Full")));
        List<Item> drops = new ArrayList<>(List.of(
                world.dropItem(block.getLocation(), new ItemStack(Material.COBBLESTONE, 2)),
                world.dropItem(block.getLocation(), named)));

        dug.dug(job, block);
        dug.onDrop(new BlockDropItemEvent(block, state, server.addPlayer(), drops));

        assertEquals(Map.of("COBBLESTONE", 2), jobs.stock(job.id()));
        assertEquals(1, drops.size());
    }

    @Test
    void dropsOfAJobThatEndedMeanwhileGoToItsOwner() {
        List<Map<String, Integer>> late = new ArrayList<>();
        stocks.late((id, items) -> late.add(items));
        jobs.close(job.id(), JobState.OPEN, JobState.CANCELLED, "test", NOW);
        var block = world.getBlockAt(1, 60, 1);
        var state = block.getState();
        List<Item> drops =
                new ArrayList<>(List.of(world.dropItem(block.getLocation(), new ItemStack(Material.COBBLESTONE))));

        dug.dug(job, block);
        dug.onDrop(new BlockDropItemEvent(block, state, server.addPlayer(), drops));

        assertEquals(Map.of(), jobs.stock(job.id()));
        assertEquals(List.of(Map.of("COBBLESTONE", 1)), late);
    }

    @Test
    void dropsWithoutANotedBreakInTheSameTickStay() {
        var block = world.getBlockAt(1, 60, 1);
        var state = block.getState();
        List<Item> drops =
                new ArrayList<>(List.of(world.dropItem(block.getLocation(), new ItemStack(Material.COBBLESTONE))));

        dug.dug(job, block);
        server.getScheduler().performOneTick();
        dug.onDrop(new BlockDropItemEvent(block, state, server.addPlayer(), drops));

        assertEquals(Map.of(), jobs.stock(job.id()));
        assertEquals(1, drops.size());
    }
}
