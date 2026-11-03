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
import org.bukkit.Location;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;

/**
 * Shows blueprints of build jobs as small glowing copies of their blocks, only to the player who asked.
 * <p>
 * Members of a company working on a build job see the blocks around them that are missing or wrong, within a radius,
 * nearest first and at most {@value #MAX_GHOSTS} of them; the ghosts follow the member as they move, checked every
 * second and only once they moved a block. A ghost goes when its block is placed as asked and comes back when it is
 * broken. A highlight shows every missing or wrong block of the job instead, nearest first and at most
 * {@value #MAX_HIGHLIGHT} of them, for {@value #HIGHLIGHT_SECONDS} seconds, once every {@value #COOLDOWN_SECONDS}
 * seconds, after which the ghosts around them come back. Ghosts are shown only to a player in the job's world and
 * only in loaded chunks; leaving the world removes them. An owner about to post a build job sees a preview of the
 * whole blueprint where it would stand, at most {@value #MAX_PREVIEW} blocks from the bottom up, for
 * {@value #PREVIEW_SECONDS} seconds; a preview can be shown once every {@value #COOLDOWN_SECONDS} seconds. A player
 * sees one set of ghosts at a time; they are removed when the player asks for others, turns them off or leaves, when
 * their job is no longer worked on, and when the plugin stops. Runs on the main thread.
 */
public final class BuildGhosts implements Listener {
    /**
     * The most ghost blocks around a player shown at once.
     */
    public static final int MAX_GHOSTS = 1024;

    /**
     * The most ghost blocks of a highlight.
     */
    public static final int MAX_HIGHLIGHT = 2048;

    /**
     * How long a highlight is shown.
     */
    public static final int HIGHLIGHT_SECONDS = 30;

    /**
     * How often the ghosts around players follow them, in ticks.
     */
    public static final long FOLLOW_TICKS = 20;

    /**
     * The most ghost blocks of a preview.
     */
    public static final int MAX_PREVIEW = 4096;

    /**
     * How long a preview is shown.
     */
    public static final int PREVIEW_SECONDS = 30;

    /**
     * How long a player waits between two previews, and between two highlights.
     */
    public static final int COOLDOWN_SECONDS = 10;

    private final Plugin plugin;
    private final InstantSource clock;
    private final Spawner spawner;
    private final Map<UUID, Shown> shown = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> previewed = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> highlighted = new ConcurrentHashMap<>();
    private volatile BuildCheck.Counted counted = BuildCheck.Counted.NONE;

    /**
     * @param plugin the plugin owning the ghost blocks
     */
    public BuildGhosts(Plugin plugin) {
        this(plugin, InstantSource.system(), BuildGhosts::display);
    }

    /**
     * Sets the positions of supplied jobs that count as placed whatever stands there, which show no ghost.
     *
     * @param counted the positions
     */
    public void counted(BuildCheck.Counted counted) {
        this.counted = counted;
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
     * What the ghosts a player sees are.
     */
    private enum Kind {
        PREVIEW,
        NEAR,
        HIGHLIGHT
    }

    /**
     * Shows a player the missing and wrong blocks of a job around them, replacing what they saw before; they follow
     * the player from then on.
     *
     * @param player    the player
     * @param job       a build job of this server
     * @param blueprint its blueprint
     * @param radius    how far around the player, in blocks
     * @return how many ghosts are shown, empty when the job's world is not loaded or the player is not in it
     */
    public OptionalInt show(Player player, Job job, Blueprint blueprint, int radius) {
        var world = world(player, job);
        if (world == null) return OptionalInt.empty();
        hide(player);
        var current =
                new Shown(Kind.NEAR, job.id(), job.area(), blueprint, new HashMap<>(), null, center(player), radius);
        shown.put(player.getUniqueId(), current);
        near(player, world, current);
        return OptionalInt.of(current.ghosts().size());
    }

    /**
     * Moves the ghosts around every player who moved a block since the last time.
     */
    public void follow() {
        for (var entry : shown.entrySet()) {
            var current = entry.getValue();
            if (current.kind() != Kind.NEAR) continue;
            var player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null) continue;
            var world = plugin.getServer().getWorld(current.area().world());
            if (world == null || !player.getWorld().equals(world)) {
                if (shown.remove(entry.getKey(), current)) remove(current);
                continue;
            }
            var center = center(player);
            if (center == current.center()) continue;
            var moved = current.at(center);
            if (!shown.replace(entry.getKey(), current, moved)) continue;
            near(player, world, moved);
        }
    }

    private @Nullable World world(Player player, Job job) {
        var world = plugin.getServer().getWorld(job.area().world());
        return world != null && player.getWorld().equals(world) ? world : null;
    }

    private static long center(Player player) {
        var at = player.getLocation();
        return key(at.getBlockX(), at.getBlockY(), at.getBlockZ());
    }

    private void near(Player player, World world, Shown current) {
        var at = player.getLocation();
        var wantedHere = open(
                world,
                current,
                at.getBlockX() - current.radius(),
                at.getBlockY() - current.radius(),
                at.getBlockZ() - current.radius(),
                at.getBlockX() + current.radius(),
                at.getBlockY() + current.radius(),
                at.getBlockZ() + current.radius(),
                at,
                current.radius(),
                MAX_GHOSTS);
        var keep = new HashSet<Long>();
        for (var spot : wantedHere) keep.add(key(spot[0], spot[1], spot[2]));
        current.ghosts().entrySet().removeIf(ghost -> {
            if (keep.contains(ghost.getKey()) && ghost.getValue().isValid()) return false;
            ghost.getValue().remove();
            return true;
        });
        for (var spot : wantedHere) {
            if (current.ghosts().containsKey(key(spot[0], spot[1], spot[2]))) continue;
            var wanted = current.blueprint().want(current.area(), spot[0], spot[1], spot[2]);
            if (wanted != null) spawn(player, world, current, spot[0], spot[1], spot[2], wanted);
        }
    }

    /**
     * @param world   the job's world
     * @param current the ghosts of the job
     * @param minX    lowest x of the box
     * @param minY    lowest y of the box
     * @param minZ    lowest z of the box
     * @param maxX    highest x of the box
     * @param maxY    highest y of the box
     * @param maxZ    highest z of the box
     * @param from    the point
     * @param radius  how far from the point, 0 for anywhere in the box
     * @param most    the most positions
     * @return the missing and wrong positions of the job within the box and in loaded chunks, nearest to the point
     *     first, as x, y, z and the squared distance
     */
    private List<int[]> open(
            World world,
            Shown current,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            Location from,
            int radius,
            int most) {
        var area = current.area();
        var types = BuildCheck.counting(BuildCheck.of(world), current.job(), counted);
        PriorityQueue<int[]> found = new PriorityQueue<>(
                Comparator.<int[]>comparingInt(spot -> spot[3]).reversed());
        var limit = (long) radius * radius;
        for (var x = Math.max(minX, area.minX()); x <= Math.min(maxX, area.maxX()); x++) {
            for (var y = Math.max(minY, area.minY()); y <= Math.min(maxY, area.maxY()); y++) {
                for (var z = Math.max(minZ, area.minZ()); z <= Math.min(maxZ, area.maxZ()); z++) {
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    var distance = distance(from, x, y, z);
                    if (radius > 0 && distance > limit) continue;
                    var wanted = current.blueprint().want(area, x, y, z);
                    if (wanted == null || types.matches(wanted, x, y, z)) continue;
                    found.add(new int[] {x, y, z, (int) Math.min(Integer.MAX_VALUE, distance)});
                    if (found.size() > most) found.poll();
                }
            }
        }
        List<int[]> nearest = new ArrayList<>(found);
        nearest.sort(Comparator.comparingInt(spot -> spot[3]));
        return nearest;
    }

    private static long distance(Location from, int x, int y, int z) {
        long dx = x - from.getBlockX(), dy = y - from.getBlockY(), dz = z - from.getBlockZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * @param player a player
     * @return the seconds the player waits before the next highlight, empty when they may see one now
     */
    public OptionalLong highlightCooldown(Player player) {
        return waiting(highlighted.get(player.getUniqueId()));
    }

    /**
     * Shows a player every missing and wrong block of a job, nearest first, for {@value #HIGHLIGHT_SECONDS} seconds,
     * unless they wait for the cooldown or nothing is missing. It replaces what they saw before; the ghosts around them
     * come back afterwards when they saw those of the same job.
     *
     * @param player    the player
     * @param job       a build job of this server
     * @param blueprint its blueprint
     * @return how many ghosts are shown, 0 when nothing is missing, empty during the cooldown, when the world is not
     *     loaded or the player is not in it
     */
    public OptionalInt highlight(Player player, Job job, Blueprint blueprint) {
        if (highlightCooldown(player).isPresent()) return OptionalInt.empty();
        var world = world(player, job);
        if (world == null) return OptionalInt.empty();
        var before = shown.get(player.getUniqueId());
        var back = before != null && before.kind() == Kind.NEAR && before.job() == job.id() ? before.radius() : 0;
        var area = job.area();
        var current = new Shown(Kind.HIGHLIGHT, job.id(), area, blueprint, new HashMap<>(), null, center(player), 0);
        var spots = open(
                world,
                current,
                area.minX(),
                area.minY(),
                area.minZ(),
                area.maxX(),
                area.maxY(),
                area.maxZ(),
                player.getLocation(),
                0,
                MAX_HIGHLIGHT);
        if (spots.isEmpty()) return OptionalInt.of(0);
        hide(player);
        highlighted.put(player.getUniqueId(), clock.instant());
        for (var spot : spots) {
            var wanted = blueprint.want(area, spot[0], spot[1], spot[2]);
            if (wanted != null) spawn(player, world, current, spot[0], spot[1], spot[2], wanted);
        }
        var id = player.getUniqueId();
        var ghosts = current.ghosts();
        var task = plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            var held = shown.get(id);
                            if (held == null || held.ghosts() != ghosts || !shown.remove(id, held)) return;
                            remove(held);
                            if (back > 0 && player.isOnline()) show(player, job, blueprint, back);
                        },
                        HIGHLIGHT_SECONDS * 20L);
        shown.put(id, current.withTask(task));
        return OptionalInt.of(current.ghosts().size());
    }

    /**
     * @param player a player
     * @return the seconds the player waits before the next preview, empty when they may see one now
     */
    public OptionalLong cooldown(Player player) {
        return waiting(previewed.get(player.getUniqueId()));
    }

    private OptionalLong waiting(@Nullable Instant last) {
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
        var current = new Shown(Kind.PREVIEW, 0, area, blueprint, new HashMap<>(), null, center(player), 0);
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
                && current.kind() == Kind.PREVIEW
                && current.area().equals(area)
                && current.blueprint().equals(blueprint);
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
        if (current != null && current.kind() == Kind.PREVIEW && shown.remove(player, current)) remove(current);
    }

    /**
     * Removes the ghost blocks of jobs no longer worked on.
     *
     * @param working whether a job is still worked on, by its id
     */
    public void prune(IntPredicate working) {
        shown.entrySet().removeIf(entry -> {
            var current = entry.getValue();
            if (current.kind() == Kind.PREVIEW || working.test(current.job())) return false;
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
        highlighted.remove(event.getPlayer().getUniqueId());
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
     * Removes the ghost of a block placed as asked, and adds one for a block missing or wrong around a player who sees
     * the ghosts of its job near them.
     *
     * @param block the block
     */
    void refresh(Block block) {
        var world = block.getWorld();
        int x = block.getX(), y = block.getY(), z = block.getZ();
        var read = BuildCheck.of(world);
        for (var entry : shown.entrySet()) {
            var current = entry.getValue();
            if (current.kind() == Kind.PREVIEW || !current.area().contains(world.getName(), x, y, z)) continue;
            var types = BuildCheck.counting(read, current.job(), counted);
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
                    && current.kind() == Kind.NEAR
                    && player.getWorld().equals(world)
                    && distance(player.getLocation(), x, y, z) <= (long) current.radius() * current.radius()
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

    /**
     * The ghosts a player sees.
     *
     * @param kind      what they are
     * @param job       id of their job, 0 for a preview
     * @param area      the area they lie in
     * @param blueprint the blueprint they show
     * @param ghosts    the ghost blocks, by position
     * @param task      removes them after a while, for a preview or highlight
     * @param center    where the player stood when the ghosts around them were last placed
     * @param radius    how far around the player the ghosts reach, 0 for a preview or highlight
     */
    private record Shown(
            Kind kind,
            int job,
            JobArea area,
            Blueprint blueprint,
            Map<Long, Entity> ghosts,
            @Nullable BukkitTask task,
            long center,
            int radius) {
        Shown withTask(@Nullable BukkitTask task) {
            return new Shown(kind, job, area, blueprint, ghosts, task, center, radius);
        }

        Shown at(long center) {
            return new Shown(kind, job, area, blueprint, ghosts, task, center, radius);
        }
    }
}
