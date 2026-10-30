/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

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

/**
 * Reads how far the work on a job is, on the server whose world holds its area. The chunks of the area are loaded
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
    private final Map<Integer, Cached> cache = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<Optional<DigCheck.Progress>>> running = new ConcurrentHashMap<>();

    /**
     * @param plugin the plugin
     * @param node   this server
     */
    public JobProgress(Plugin plugin, UUID node) {
        this.plugin = plugin;
        this.node = node;
    }

    /**
     * @param job   the job
     * @param fresh whether the result may be at most {@value #FRESH_SECONDS} seconds old instead of
     *              {@value #CACHE_SECONDS}
     * @return how far the work is, empty for a job of another server or a world not loaded
     */
    public CompletableFuture<Optional<DigCheck.Progress>> of(Job job, boolean fresh) {
        if (!job.node().equals(node)) return CompletableFuture.completedFuture(Optional.empty());
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
                .thenApplyAsync(ignored -> DigCheck.progress(area, DigCheck.of(world)), mainThread)
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

    private record Cached(DigCheck.Progress progress, Instant at) {}
}
