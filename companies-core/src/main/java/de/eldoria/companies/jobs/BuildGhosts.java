/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;

/**
 * Shows blueprints of build jobs as small glowing copies of their blocks, only to the player who asked.
 * <p>
 * Members of a company working on a build job see the blocks of one layer that are missing or wrong, at most
 * {@value #MAX_GHOSTS} of them; a ghost goes when its block is placed as asked and comes back when it is broken. An
 * owner about to post a build job sees a preview of the whole blueprint where it would stand, at most
 * {@value #MAX_PREVIEW} blocks from the bottom up, for {@value #PREVIEW_SECONDS} seconds; a preview can be shown once
 * every {@value #COOLDOWN_SECONDS} seconds. A player sees one set of ghosts at a time; they are removed when the player
 * asks for others, turns them off or leaves, when their job is no longer worked on, and when the plugin stops. Runs on
 * the main thread.
 */
public final class BuildGhosts implements Listener {
    /**
     * The most ghost blocks of a layer shown at once to one player.
     */
    public static final int MAX_GHOSTS = 1024;

    /**
     * The most ghost blocks of a preview.
     */
    public static final int MAX_PREVIEW = 4096;

    /**
     * How long a preview is shown.
     */
    public static final int PREVIEW_SECONDS = 30;

    /**
     * How long a player waits between two previews.
     */
    public static final int COOLDOWN_SECONDS = 10;

    private static final int PREVIEW = -1;

    private final Plugin plugin;
    private final InstantSource clock;
    private final Spawner spawner;
    private final Map<UUID, Shown> shown = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> previewed = new ConcurrentHashMap<>();

    /**
     * @param plugin the plugin owning the ghost blocks
     */
    public BuildGhosts(Plugin plugin) {
        this(plugin, InstantSource.system(), BuildGhosts::display);
    }

    /**
     * @param plugin  the plugin owning the ghost blocks
     * @param clock   tells the time, for the cooldown of previews
     * @param spawner spawns one ghost block
     */
    BuildGhosts(Plugin plugin, InstantSource clock, Spawner spawner) {
        this.plugin = plugin;
        this.clock = clock;
        this.spawner = spawner;
    }

    /**
     * Spawns a ghost block nobody sees yet.
     */
    @FunctionalInterface
    interface Spawner {
        /**
         * @param world the world
         * @param x     x of the block
         * @param y     y of the block
         * @param z     z of the block
         * @param data  the block shown
         * @param dig   whether it marks a position to dig out, which glows red
         * @return the ghost
         */
        Entity spawn(World world, int x, int y, int z, BlockData data, boolean dig);
    }

    /**
     * What a layer of a build job still lacks.
     *
     * @param y     the height of the layer
     * @param asked how many blocks the blueprint asks for in it
     * @param open  how many of them are missing or wrong
     */
    public record Layer(int y, int asked, int open) {}

    /**
     * @param area      a build job's area
     * @param blueprint its blueprint
     * @param types     reads the world
     * @param y         the height of a layer
     * @return what the layer still lacks
     */
    public static Layer layer(JobArea area, Blueprint blueprint, BuildCheck.Types types, int y) {
        var asked = 0;
        var open = 0;
        for (var x = area.minX(); x <= area.maxX(); x++) {
            for (var z = area.minZ(); z <= area.maxZ(); z++) {
                var wanted = blueprint.want(area, x, y, z);
                if (wanted == null) continue;
                asked++;
                if (!types.matches(wanted, x, y, z)) open++;
            }
        }
        return new Layer(y, asked, open);
    }

    /**
     * @param area      a build job's area
     * @param blueprint its blueprint
     * @param types     reads the world
     * @return the lowest layer that still lacks a block, the lowest layer when none does
     */
    public static int firstOpen(JobArea area, Blueprint blueprint, BuildCheck.Types types) {
        for (var y = area.minY(); y <= area.maxY(); y++) {
            if (layer(area, blueprint, types, y).open() > 0) return y;
        }
        return area.minY();
    }

    /**
     * Shows a player the missing and wrong blocks of a layer, replacing what they saw before.
     *
     * @param player    the player
     * @param job       a build job of this server
     * @param blueprint its blueprint
     * @param y         the height of the layer, kept within the area
     * @return what the layer still lacks, empty when the job's world is not loaded
     */
    public Optional<Layer> show(Player player, Job job, Blueprint blueprint, int y) {
        hide(player);
        var area = job.area();
        var world = plugin.getServer().getWorld(area.world());
        if (world == null) return Optional.empty();
        var layerY = Math.clamp(y, area.minY(), area.maxY());
        var types = BuildCheck.of(world);
        var current = new Shown(job.id(), layerY, area, blueprint, new HashMap<>(), null);
        for (var x = area.minX(); x <= area.maxX() && current.ghosts().size() < MAX_GHOSTS; x++) {
            for (var z = area.minZ(); z <= area.maxZ() && current.ghosts().size() < MAX_GHOSTS; z++) {
                var wanted = blueprint.want(area, x, layerY, z);
                if (wanted == null || types.matches(wanted, x, layerY, z)) continue;
                spawn(player, world, current, x, layerY, z, wanted);
            }
        }
        shown.put(player.getUniqueId(), current);
        return Optional.of(layer(area, blueprint, types, layerY));
    }

    /**
     * @param player a player
     * @return the seconds the player waits before the next preview, empty when they may see one now
     */
    public OptionalLong cooldown(Player player) {
        var last = previewed.get(player.getUniqueId());
        if (last == null) return OptionalLong.empty();
        var left = Duration.between(clock.instant(), last.plusSeconds(COOLDOWN_SECONDS));
        return left.isPositive() ? OptionalLong.of(Math.max(1, left.toSeconds())) : OptionalLong.empty();
    }

    /**
     * Shows a player a blueprint where it would stand, replacing what they saw before, unless they wait for the
     * cooldown.
     *
     * @param player    the player
     * @param area      the area the blueprint covers
     * @param blueprint the blueprint
     * @return how many of its blocks are shown, empty during the cooldown or when the world is not loaded
     */
    public OptionalInt preview(Player player, JobArea area, Blueprint blueprint) {
        if (cooldown(player).isPresent()) return OptionalInt.empty();
        var world = plugin.getServer().getWorld(area.world());
        if (world == null) return OptionalInt.empty();
        hide(player);
        previewed.put(player.getUniqueId(), clock.instant());
        var current = new Shown(PREVIEW, area.minY(), area, blueprint, new HashMap<>(), null);
        for (var y = area.minY(); y <= area.maxY() && current.ghosts().size() < MAX_PREVIEW; y++) {
            for (var x = area.minX(); x <= area.maxX() && current.ghosts().size() < MAX_PREVIEW; x++) {
                for (var z = area.minZ(); z <= area.maxZ() && current.ghosts().size() < MAX_PREVIEW; z++) {
                    var wanted = blueprint.want(area, x, y, z);
                    if (wanted != null) spawn(player, world, current, x, y, z, wanted);
                }
            }
        }
        var task = plugin.getServer()
                .getScheduler()
                .runTaskLater(plugin, () -> hidePreview(player.getUniqueId()), PREVIEW_SECONDS * 20L);
        shown.put(player.getUniqueId(), current.withTask(task));
        return OptionalInt.of(current.ghosts().size());
    }

    /**
     * @param player a player
     * @param area      an area
     * @param blueprint a blueprint
     * @return whether the player sees a preview of that blueprint in that area now
     */
    public boolean previewing(Player player, JobArea area, Blueprint blueprint) {
        var current = shown.get(player.getUniqueId());
        return current != null
                && current.job() == PREVIEW
                && current.area().equals(area)
                && current.blueprint().equals(blueprint);
    }

    /**
     * @param player a player
     * @param job    id of a job
     * @return the layer of the job the player sees, empty when they see none of it
     */
    public Optional<Integer> layerOf(Player player, int job) {
        var current = shown.get(player.getUniqueId());
        return current == null || current.job() != job ? Optional.empty() : Optional.of(current.y());
    }

    /**
     * Removes the ghost blocks a player sees.
     *
     * @param player the player
     */
    public void hide(Player player) {
        remove(shown.remove(player.getUniqueId()));
    }

    /**
     * Removes the preview a player sees, keeping the ghosts of a job.
     *
     * @param player the player
     */
    public void hidePreview(UUID player) {
        var current = shown.get(player);
        if (current != null && current.job() == PREVIEW && shown.remove(player, current)) remove(current);
    }

    /**
     * Removes the ghost blocks of jobs no longer worked on.
     *
     * @param working whether a job is still worked on, by its id
     */
    public void prune(IntPredicate working) {
        shown.entrySet().removeIf(entry -> {
            var current = entry.getValue();
            if (current.job() == PREVIEW || working.test(current.job())) return false;
            remove(current);
            return true;
        });
    }

    /**
     * Removes every ghost block.
     */
    public void stop() {
        shown.values().forEach(BuildGhosts::remove);
        shown.clear();
    }

    /**
     * @param event the player leaving
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hide(event.getPlayer());
        previewed.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Refreshes the ghost of a placed block on the next tick, once the block is turned as asked.
     *
     * @param event the placement
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaced(BlockPlaceEvent event) {
        refreshSoon(event.getBlockPlaced());
    }

    /**
     * Brings the ghost of a broken block back on the next tick.
     *
     * @param event the break
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBroken(BlockBreakEvent event) {
        refreshSoon(event.getBlock());
    }

    private void refreshSoon(Block block) {
        if (shown.isEmpty()) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> refresh(block));
    }

    /**
     * Removes the ghost of a block placed as asked and adds one for a block missing or wrong, for every player who
     * sees the block's layer of a job.
     *
     * @param block the block
     */
    void refresh(Block block) {
        var world = block.getWorld();
        int x = block.getX(), y = block.getY(), z = block.getZ();
        var types = BuildCheck.of(world);
        for (var entry : shown.entrySet()) {
            var current = entry.getValue();
            if (current.job() == PREVIEW || current.y() != y || !current.area().contains(world.getName(), x, y, z)) {
                continue;
            }
            var wanted = current.blueprint().want(current.area(), x, y, z);
            if (wanted == null) continue;
            var key = key(x, y, z);
            if (types.matches(wanted, x, y, z)) {
                var ghost = current.ghosts().remove(key);
                if (ghost != null) ghost.remove();
                continue;
            }
            var player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null
                    && !current.ghosts().containsKey(key)
                    && current.ghosts().size() < MAX_GHOSTS) {
                spawn(player, world, current, x, y, z, wanted);
            }
        }
    }

    /**
     * @param want what a blueprint asks for
     * @return its block data, or the type alone when this server can not read the data; red glass marks a position to
     *     dig out
     */
    static BlockData data(Blueprint.Want want) {
        if (want.type().isAir()) return Material.RED_STAINED_GLASS.createBlockData();
        try {
            return Bukkit.createBlockData(want.data());
        } catch (IllegalArgumentException e) {
            return want.type().createBlockData();
        }
    }

    private void spawn(Player player, World world, Shown current, int x, int y, int z, Blueprint.Want wanted) {
        var ghost = spawner.spawn(world, x, y, z, data(wanted), wanted.type().isAir());
        player.showEntity(plugin, ghost);
        current.ghosts().put(key(x, y, z), ghost);
    }

    private static Entity display(World world, int x, int y, int z, BlockData data, boolean dig) {
        return world.spawn(world.getBlockAt(x, y, z).getLocation(), BlockDisplay.class, display -> {
            display.setBlock(data);
            display.setVisibleByDefault(false);
            display.setPersistent(false);
            display.setGlowing(true);
            if (dig) display.setGlowColorOverride(Color.RED);
            display.setTransformation(new Transformation(
                    new Vector3f(0.2f, 0.2f, 0.2f),
                    new AxisAngle4f(),
                    new Vector3f(0.6f, 0.6f, 0.6f),
                    new AxisAngle4f()));
        });
    }

    private static void remove(@Nullable Shown current) {
        if (current == null) return;
        var task = current.task();
        if (task != null) task.cancel();
        current.ghosts().values().forEach(Entity::remove);
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | ((long) y & 0xFFF);
    }

    private record Shown(
            int job,
            int y,
            JobArea area,
            Blueprint blueprint,
            Map<Long, Entity> ghosts,
            @Nullable BukkitTask task) {
        Shown withTask(BukkitTask task) {
            return new Shown(job, y, area, blueprint, ghosts, task);
        }
    }
}
