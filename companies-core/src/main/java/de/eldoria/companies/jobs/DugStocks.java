/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.data.wrapper.job.Job;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Puts the drops of blocks a member dug as a job asks into the job's stock, for jobs whose owner chose so. A break is
 * matched to its drops by world and position within the same tick. Only plain items go into the stock, as a stock
 * counts items by type; items with data of their own, such as a shulker box with contents, drop as usual.
 */
public final class DugStocks implements Listener {
    private final Server server;
    private final JobStocks stocks;
    private final Map<Spot, Dug> dug = new ConcurrentHashMap<>();

    /**
     * @param server the server, for its tick
     * @param stocks the stock of jobs
     */
    public DugStocks(Server server, JobStocks stocks) {
        this.server = server;
        this.stocks = stocks;
    }

    /**
     * A position of a world.
     *
     * @param world name of the world
     * @param x     x of the block
     * @param y     y of the block
     * @param z     z of the block
     */
    private record Spot(String world, int x, int y, int z) {
        static Spot of(Block block) {
            return new Spot(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        }
    }

    /**
     * A block dug for a job.
     *
     * @param job  id of the job
     * @param tick the tick it was broken in
     */
    private record Dug(int job, int tick) {}

    /**
     * Notes a block a member broke for a job whose dug blocks go into its stock, before it drops.
     *
     * @param job   the job
     * @param block the broken block
     */
    public void dug(Job job, Block block) {
        var tick = server.getCurrentTick();
        dug.values().removeIf(entry -> entry.tick() != tick);
        dug.put(Spot.of(block), new Dug(job.id(), tick));
    }

    /**
     * Takes the plain drops of a block dug for a job into its stock.
     *
     * @param event the drops of a broken block
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        var noted = dug.remove(Spot.of(event.getBlock()));
        if (noted == null || noted.tick() != server.getCurrentTick()) return;
        Map<String, Integer> taken = new TreeMap<>();
        event.getItems().removeIf(drop -> {
            var item = drop.getItemStack();
            if (!item.isSimilar(new ItemStack(item.getType()))) return false;
            taken.merge(item.getType().name(), item.getAmount(), Integer::sum);
            return true;
        });
        stocks.give(noted.job(), taken);
    }
}
