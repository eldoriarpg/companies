/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.util.Threading;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

/**
 * Reads how far the work on a job is, by the check of its kind ({@link DigCheck}, {@link TerraformCheck}, {@link BuildCheck}), on the server whose world holds its area. The chunks of the area are loaded
 * without blocking the main thread, each held by a plugin ticket from when it is loaded until the area is checked on
 * the main thread. A result
 * is kept for {@value #CACHE_SECONDS} seconds per job; a fresh check, as finishing a job asks for, still reuses one at
 * most {@value #FRESH_SECONDS} seconds old, and checks of one job asked for at the same time share one scan. Kept
 * results older than the cache are dropped as new ones come in.
 */
public final class JobProgress {
    /**
     * How long a checked area is kept, in seconds.
     */
    public static final int CACHE_SECONDS = 10;

    /**
     * How old a kept result may be to count as fresh, in seconds.
     */
    public static final int FRESH_SECONDS = 2;

    private final Plugin plugin;
    private final UUID node;
    private final Blueprints blueprints;
    private final IntSupplier matchPercent;
    private final Map<Integer, Cached> cache = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<Optional<DigCheck.Progress>>> running = new ConcurrentHashMap<>();

    /**
     * @param plugin       the plugin
     * @param node         this server
     * @param blueprints   the blueprints of build jobs
     * @param matchPercent the share of a blueprint that must match for a build job to be done
     */
    public JobProgress(Plugin plugin, UUID node, Blueprints blueprints, IntSupplier matchPercent) {
        this.plugin = plugin;
        this.node = node;
        this.blueprints = blueprints;
        this.matchPercent = matchPercent;
    }

    /**
     * @param job   the job
     * @param fresh whether the result may be at most {@value #FRESH_SECONDS} seconds old instead of
     *              {@value #CACHE_SECONDS}
     * @return how far the work is, empty for a job of another server, a world not loaded or a kind without a check
     */
    public CompletableFuture<Optional<DigCheck.Progress>> of(Job job, boolean fresh) {
        if (!job.node().equals(node) || !job.kind().area() || job.kind() == JobKind.HARVEST) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        var world = plugin.getServer().getWorld(job.area().world());
        if (world == null) return CompletableFuture.completedFuture(Optional.empty());
        var kept = cache.get(job.id());
        var age = Duration.ofSeconds(fresh ? FRESH_SECONDS : CACHE_SECONDS);
        if (kept != null && kept.at().plus(age).isAfter(Instant.now())) {
            return CompletableFuture.completedFuture(Optional.of(kept.progress()));
        }
        return running.computeIfAbsent(
                job.id(), id -> scan(job, world).whenComplete((result, err) -> running.remove(id)));
    }

    private CompletableFuture<Optional<DigCheck.Progress>> scan(Job job, World world) {
        var area = job.area();
        var blueprint = job.kind() == JobKind.BUILD ? blueprints.of(job.id()).orElse(null) : null;
        if (job.kind() == JobKind.BUILD && blueprint == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        var percent = matchPercent.getAsInt();
        List<int[]> chunks = new ArrayList<>();
        List<CompletableFuture<?>> loads = new ArrayList<>();
        var mainThread = Threading.mainThread(plugin);
        for (var x = area.minX() >> 4; x <= area.maxX() >> 4; x++) {
            for (var z = area.minZ() >> 4; z <= area.maxZ() >> 4; z++) {
                var cx = x;
                var cz = z;
                chunks.add(new int[] {x, z});
                loads.add(world.getChunkAtAsync(x, z)
                        .thenRunAsync(() -> world.addPluginChunkTicket(cx, cz, plugin), mainThread));
            }
        }
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new))
                .thenApplyAsync(
                        ignored -> blueprint == null
                                ? DigCheck.progress(area, blocks(job, world))
                                : BuildCheck.progress(area, blueprint, BuildCheck.of(world), percent),
                        mainThread)
                .whenCompleteAsync(
                        (result, err) -> {
                            for (var chunk : chunks) world.removePluginChunkTicket(chunk[0], chunk[1], plugin);
                        },
                        mainThread)
                .thenApply(progress -> {
                    var now = Instant.now();
                    cache.values()
                            .removeIf(entry ->
                                    entry.at().plusSeconds(CACHE_SECONDS).isBefore(now));
                    cache.put(job.id(), new Cached(progress, now));
                    return Optional.of(progress);
                });
    }

    /**
     * @param job   a job with an area
     * @param world the world of its area
     * @return the reader of whether each block is as the job asks
     */
    private static DigCheck.Blocks blocks(Job job, World world) {
        var target = job.target();
        if (job.kind() == JobKind.TERRAFORM && target != null) return TerraformCheck.of(world, target);
        return DigCheck.of(world);
    }

    private record Cached(DigCheck.Progress progress, Instant at) {}
}
