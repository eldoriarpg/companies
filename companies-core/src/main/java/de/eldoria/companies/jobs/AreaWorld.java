/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.eldoria.companies.data.wrapper.job.JobArea;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.HeightMap;
import org.bukkit.Server;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Reads the world of a stored area for the dashboard: its top layer as a small map, and where a blueprint stands when
 * the owner places it on the area. The chunks are loaded without blocking the server and each is copied as it
 * arrives, so the work spreads over the ticks; a drawn area is kept for half a minute and each player draws one area
 * at a time.
 */
public final class AreaWorld {
    /**
     * The most columns along x or z a top layer is drawn for.
     */
    public static final int MAX_SIDE = 128;

    private static final String NOTHING = "";
    private static final long KEPT_SECONDS = 30;

    private final Server server;
    private final Executor mainThread;
    private final ChunkLoader chunks;
    private final Set<UUID> drawing = ConcurrentHashMap.newKeySet();
    private final Cache<JobArea, CompletableFuture<@Nullable TopLayer>> drawn = CacheBuilder.newBuilder()
            .expireAfterWrite(KEPT_SECONDS, TimeUnit.SECONDS)
            .build();

    /**
     * @param server     the server, whose worlds hold the areas
     * @param mainThread runs tasks on the main thread
     */
    public AreaWorld(Server server, Executor mainThread) {
        this(server, mainThread, World::getChunkAtAsync);
    }

    /**
     * @param server     the server, whose worlds hold the areas
     * @param mainThread runs tasks on the main thread
     * @param chunks     loads a chunk without blocking the server; its future completes on the main thread
     */
    public AreaWorld(Server server, Executor mainThread, ChunkLoader chunks) {
        this.server = server;
        this.mainThread = mainThread;
        this.chunks = chunks;
    }

    /**
     * Loads a chunk of a world.
     */
    @FunctionalInterface
    public interface ChunkLoader {
        /**
         * @param world the world
         * @param x     x of the chunk
         * @param z     z of the chunk
         * @return the chunk once it is loaded, on the main thread
         */
        CompletableFuture<Chunk> load(World world, int x, int z);
    }

    /**
     * The top layer of an area, seen from above.
     *
     * @param width   columns along x
     * @param length  columns along z
     * @param palette the colours as {@code #rrggbb}, an empty text for a column with nothing in the area
     * @param cells   the palette index of each column, row by row along z, each row along x
     */
    public record TopLayer(int width, int length, List<String> palette, int[] cells) {}

    /**
     * The colour of the top block of a column.
     */
    @FunctionalInterface
    public interface Columns {
        /**
         * @param x x of the column
         * @param z z of the column
         * @return the colour as {@code #rrggbb}, null when the column holds nothing within the area
         */
        @Nullable
        String color(int x, int z);
    }

    /**
     * Draws the top layer of an area from the colours of its columns.
     *
     * @param area    the area, at most {@link #MAX_SIDE} columns along x and z
     * @param columns the colour of each column
     * @return the top layer
     */
    public static TopLayer render(JobArea area, Columns columns) {
        var width = area.maxX() - area.minX() + 1;
        var length = area.maxZ() - area.minZ() + 1;
        Map<String, Integer> palette = new LinkedHashMap<>();
        var cells = new int[width * length];
        for (var dz = 0; dz < length; dz++) {
            for (var dx = 0; dx < width; dx++) {
                var color = columns.color(area.minX() + dx, area.minZ() + dz);
                var key = color == null ? NOTHING : color;
                cells[dz * width + dx] = palette.computeIfAbsent(key, ignored -> palette.size());
            }
        }
        return new TopLayer(width, length, List.copyOf(palette.keySet()), cells);
    }

    /**
     * @param area an area
     * @return whether its top layer is small enough to draw
     */
    public static boolean drawable(JobArea area) {
        return area.maxX() - area.minX() < MAX_SIDE && area.maxZ() - area.minZ() < MAX_SIDE;
    }

    /**
     * What came of drawing an area.
     */
    public sealed interface Drawn {
        /**
         * The area was drawn.
         *
         * @param top its top layer
         */
        record Done(TopLayer top) implements Drawn {}

        /**
         * The area's world is not on this server.
         */
        record NoWorld() implements Drawn {}

        /**
         * The player still waits for another area to be drawn.
         */
        record Busy() implements Drawn {}
    }

    /**
     * Reads the top layer of an area: the highest block with a map colour of each column within the area's height.
     * Call {@link #drawable(JobArea)} first.
     *
     * @param player the player asking, who draws one area at a time
     * @param area   the area
     * @return what came of it
     */
    public CompletableFuture<Drawn> topLayer(UUID player, JobArea area) {
        var kept = drawn.getIfPresent(area);
        if (kept != null) return kept.thenApply(AreaWorld::drawn);
        if (!drawing.add(player)) return CompletableFuture.completedFuture(new Drawn.Busy());
        CompletableFuture<@Nullable TopLayer> top;
        try {
            top = drawn.get(area, () -> draw(area).orTimeout(KEPT_SECONDS, TimeUnit.SECONDS));
        } catch (ExecutionException | RuntimeException e) {
            drawing.remove(player);
            return CompletableFuture.failedFuture(e.getCause() == null ? e : e.getCause());
        }
        top.whenComplete((done, err) -> {
            drawing.remove(player);
            if (err != null) drawn.invalidate(area);
        });
        return top.thenApply(AreaWorld::drawn);
    }

    private static Drawn drawn(@Nullable TopLayer top) {
        return top == null ? new Drawn.NoWorld() : new Drawn.Done(top);
    }

    private CompletableFuture<@Nullable TopLayer> draw(JobArea area) {
        return loaded(area, chunk -> chunk.getChunkSnapshot(true, false, false))
                .thenApplyAsync(loaded -> loaded == null ? null : render(area, (x, z) -> color(area, loaded, x, z)));
    }

    private static @Nullable String color(JobArea area, Loaded<ChunkSnapshot> loaded, int x, int z) {
        var snapshot = loaded.chunks().get(key(x >> 4, z >> 4));
        if (snapshot == null) return null;
        var top = Math.min(area.maxY(), snapshot.getHighestBlockYAt(x & 15, z & 15));
        for (var y = top; y >= area.minY(); y--) {
            var block = snapshot.getBlockData(x & 15, y, z & 15);
            if (block.getMaterial().isAir()) continue;
            var color = block.getMapColor();
            if (color.asRGB() == 0) continue;
            return "#%02x%02x%02x".formatted(color.getRed(), color.getGreen(), color.getBlue());
        }
        return null;
    }

    /**
     * Where a blueprint stands when its owner placed it on an area.
     */
    public sealed interface Placed {
        /**
         * It stands there.
         *
         * @param blueprint the blueprint, with the ground it reaches into to be dug out
         * @param area      the area it covers
         */
        record Done(Blueprint blueprint, JobArea area) implements Placed {}

        /**
         * Seen from above it reaches out of the area.
         */
        record Outside() implements Placed {}

        /**
         * The area's world is not on this server.
         */
        record NoWorld() implements Placed {}

        /**
         * It asks for nothing there.
         */
        record Nothing() implements Placed {}
    }

    /**
     * Places a blueprint on an area as if its owner stood at its origin, on the ground of the origin's column. An
     * origin outside the blueprint's box, as a schematic may have, takes the ground of the nearest column of the box,
     * so the height follows the ground the owner sees on the map. The ground the blueprint reaches into is to be dug
     * out.
     *
     * @param area      the area
     * @param blueprint the blueprint, turned
     * @param x         how far its west side lies east of the area's west side
     * @param z         how far its north side lies south of the area's north side
     * @return where it stands
     */
    public CompletableFuture<Placed> place(JobArea area, Blueprint blueprint, int x, int z) {
        var probe = blueprint.area(area.world(), 0, 0, 0);
        var originX = area.minX() + x - probe.minX();
        var originZ = area.minZ() + z - probe.minZ();
        var footprint = blueprint.area(area.world(), originX, 0, originZ);
        if (footprint.minX() < area.minX()
                || footprint.maxX() > area.maxX()
                || footprint.minZ() < area.minZ()
                || footprint.maxZ() > area.maxZ()) {
            return CompletableFuture.completedFuture(new Placed.Outside());
        }
        var groundX = Math.clamp(originX, footprint.minX(), footprint.maxX());
        var groundZ = Math.clamp(originZ, footprint.minZ(), footprint.maxZ());
        return loaded(footprint, Function.identity())
                .thenApplyAsync(
                        loaded -> {
                            if (loaded == null) return new Placed.NoWorld();
                            var world = loaded.world();
                            var y = world.getHighestBlockYAt(groundX, groundZ, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
                            try {
                                var dug = blueprint.dug(
                                        world.getName(),
                                        originX,
                                        y,
                                        originZ,
                                        BuildCheck.of(world),
                                        (cx, cz) ->
                                                world.getHighestBlockYAt(cx, cz, HeightMap.MOTION_BLOCKING_NO_LEAVES));
                                return new Placed.Done(dug, dug.area(world.getName(), originX, y, originZ));
                            } catch (IllegalArgumentException e) {
                                return new Placed.Nothing();
                            }
                        },
                        mainThread);
    }

    /**
     * What was taken of each chunk of an area as it arrived on the main thread.
     *
     * @param world  the area's world
     * @param chunks what was taken of each chunk, by {@link #key(int, int)}
     * @param <T>    what is taken of a chunk
     */
    private record Loaded<T>(World world, Map<Long, T> chunks) {}

    /**
     * Loads the chunks of an area without blocking the server and takes what is needed of each as it arrives.
     *
     * @return what was taken, null when the area's world is not on this server
     */
    private <T> CompletableFuture<@Nullable Loaded<T>> loaded(JobArea area, Function<Chunk, T> take) {
        return CompletableFuture.supplyAsync(() -> server.getWorld(area.world()), mainThread)
                .thenCompose(world -> {
                    if (world == null) return CompletableFuture.completedFuture(null);
                    Map<Long, T> taken = new ConcurrentHashMap<>();
                    List<CompletableFuture<?>> loading = new ArrayList<>();
                    for (var cx = area.minX() >> 4; cx <= area.maxX() >> 4; cx++) {
                        for (var cz = area.minZ() >> 4; cz <= area.maxZ() >> 4; cz++) {
                            var key = key(cx, cz);
                            loading.add(
                                    chunks.load(world, cx, cz).thenAccept(chunk -> taken.put(key, take.apply(chunk))));
                        }
                    }
                    return CompletableFuture.allOf(loading.toArray(CompletableFuture[]::new))
                            .thenApply(done -> new Loaded<>(world, taken));
                });
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }
}
