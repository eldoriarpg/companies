/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.configuration.elements.jobs.JobSettings;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobLand;
import de.eldoria.companies.jobs.hooks.JobHooks;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Whether an area job may be posted on an area of this server: the area must lie within its world's height, and on the
 * owner's land, or on unowned land where the server allows it. Commands and the dashboard post with the same check.
 */
public final class AreaChecks {
    private static final long MAIN_THREAD_SECONDS = 10;

    private final Server server;
    private final JobHooks hooks;
    private final Supplier<JobSettings> settings;
    private final Executor mainThread;

    /**
     * @param server     the server, whose worlds hold the areas
     * @param hooks      the land plugins
     * @param settings   the settings of jobs
     * @param mainThread runs tasks on the main thread
     */
    public AreaChecks(Server server, JobHooks hooks, Supplier<JobSettings> settings, Executor mainThread) {
        this.server = server;
        this.hooks = hooks;
        this.settings = settings;
        this.mainThread = mainThread;
    }

    /**
     * What the check found.
     */
    public sealed interface Checked {
        /**
         * The area's world is not loaded on this server.
         *
         * @param world the name of the world
         */
        record NoWorld(String world) implements Checked {}

        /**
         * The area leaves its world's height.
         *
         * @param min the lowest block of the world
         * @param max the highest block of the world
         */
        record Height(int min, int max) implements Checked {}

        /**
         * The area touches land the owner may not change.
         *
         * @param ref the plot or region it touches, null when unknown
         */
        record Foreign(@Nullable String ref) implements Checked {}

        /**
         * The area lies on unowned land where the server does not allow jobs.
         */
        record Unowned() implements Checked {}

        /**
         * A job may be posted on the area.
         *
         * @param land whose land it lies on
         * @param ref  the plot or region, null for unowned land
         */
        record Land(JobLand land, @Nullable String ref) implements Checked {}
    }

    /**
     * Checks an area. Call it on the main thread.
     *
     * @param owner the player who would post the job
     * @param area  the area
     * @return what was found
     */
    public Checked check(OfflinePlayer owner, JobArea area) {
        var world = server.getWorld(area.world());
        if (world == null) return new Checked.NoWorld(area.world());
        if (area.minY() < world.getMinHeight() || area.maxY() >= world.getMaxHeight()) {
            return new Checked.Height(world.getMinHeight(), world.getMaxHeight() - 1);
        }
        return switch (hooks.decide(owner, area, settings.get().unownedAllowed(area.world()))) {
            case JobHooks.Decision.Refused refused -> new Checked.Foreign(refused.ref());
            case JobHooks.Decision.Unowned unowned -> new Checked.Unowned();
            case JobHooks.Decision.Land land -> new Checked.Land(land.land(), land.ref());
        };
    }

    /**
     * Checks an area on the main thread and waits for it. Call it off the main thread.
     *
     * @param owner the player who would post the job
     * @param area  the area
     * @return what was found
     * @throws CompletionException when the main thread did not answer in time or failed
     */
    public Checked checkOnMain(UUID owner, JobArea area) {
        return CompletableFuture.supplyAsync(() -> check(server.getOfflinePlayer(owner), area), mainThread)
                .orTimeout(MAIN_THREAD_SECONDS, TimeUnit.SECONDS)
                .join();
    }
}
