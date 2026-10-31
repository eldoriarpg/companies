/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.api.write.JobProblem;
import de.eldoria.companies.api.write.JobRequest;
import de.eldoria.companies.api.write.PostJobResult;
import de.eldoria.companies.components.job.BuildBlock;
import de.eldoria.companies.components.job.IJob;
import de.eldoria.companies.components.job.IJobArea;
import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.data.repository.AJobData;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobLand;
import de.eldoria.companies.data.wrapper.job.JobTarget;
import de.eldoria.companies.jobs.Blueprint;
import de.eldoria.companies.jobs.JobService;
import de.eldoria.companies.jobs.TerraformCheck;
import de.eldoria.companies.jobs.hooks.JobHooks;
import de.eldoria.companies.util.Times;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Reads jobs from the repository and posts them through the job service. The world and the land plugins are asked on
 * the main thread, as {@code /order job} does; the service runs off it.
 */
final class JobApiImpl implements JobApi {
    private static final JobArea NO_PLACE = new JobArea("", 0, 0, 0, 0, 0, 0);

    private final AJobData jobs;
    private final WriteAccess writes;
    private final Function<UUID, OfflinePlayer> players;
    private final Executor executor;

    JobApiImpl(AJobData jobs, WriteAccess writes, Function<UUID, OfflinePlayer> players, Executor executor) {
        this.jobs = jobs;
        this.writes = writes;
        this.players = players;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Optional<? extends IJob>> byId(int id) {
        return CompletableFuture.<Optional<? extends IJob>>supplyAsync(
                () -> jobs.job(id).map(ApiJob::new), executor);
    }

    @Override
    public CompletableFuture<List<? extends IJob>> open(int limit) {
        positive(limit);
        return CompletableFuture.<List<? extends IJob>>supplyAsync(
                () -> shown(jobs.open(Times.now(), limit)), executor);
    }

    @Override
    public CompletableFuture<List<? extends IJob>> ofOwner(UUID owner, int limit) {
        positive(limit);
        return CompletableFuture.<List<? extends IJob>>supplyAsync(() -> shown(jobs.ofOwner(owner, limit)), executor);
    }

    @Override
    public CompletableFuture<List<? extends IJob>> ofCompany(int company) {
        return CompletableFuture.<List<? extends IJob>>supplyAsync(() -> shown(jobs.ofCompany(company)), executor);
    }

    @Override
    public CompletableFuture<PostJobResult> post(JobRequest request) {
        return writes.run(services -> {
            var refused = refused(services, request);
            if (refused != null) return CompletableFuture.completedFuture(new PostJobResult.Rejected(refused));
            var blueprint = blueprint(request);
            var area = area(request, blueprint);
            return CompletableFuture.supplyAsync(
                            () -> land(services, request.owner(), request.kind(), area), services.mainThread())
                    .thenApplyAsync(land -> post(services, request, area, blueprint, land), executor);
        });
    }

    private static @Nullable JobProblem refused(CompaniesApiImpl.Writes services, JobRequest request) {
        var surface = request.surface();
        if (surface != null && TerraformCheck.block(surface.name()) == null) return JobProblem.SURFACE;
        var build = request.build();
        if (build != null
                && volume(build.blocks()) > services.jobSettings().get().maxVolume(JobKind.BUILD)) {
            return JobProblem.VOLUME;
        }
        return null;
    }

    private static long volume(List<BuildBlock> blocks) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var block : blocks) {
            minX = Math.min(minX, block.x());
            minY = Math.min(minY, block.y());
            minZ = Math.min(minZ, block.z());
            maxX = Math.max(maxX, block.x());
            maxY = Math.max(maxY, block.y());
            maxZ = Math.max(maxZ, block.z());
        }
        return ((long) maxX - minX + 1) * ((long) maxY - minY + 1) * ((long) maxZ - minZ + 1);
    }

    private static List<IJob> shown(List<Job> found) {
        return found.stream().<IJob>map(ApiJob::new).toList();
    }

    private static void positive(int limit) {
        if (limit < 1) throw new IllegalArgumentException("The limit must be at least 1");
    }

    private static @Nullable Blueprint blueprint(JobRequest request) {
        var build = request.build();
        if (build == null) return null;
        return Blueprint.of(build.blocks().stream()
                .map(block -> new Blueprint.Cell(block.x(), block.y(), block.z(), block.type()))
                .toList());
    }

    private static JobArea area(JobRequest request, @Nullable Blueprint blueprint) {
        var build = request.build();
        if (blueprint != null && build != null) return blueprint.area(build.world(), build.x(), build.y(), build.z());
        IJobArea box = request.area();
        if (box == null) return NO_PLACE;
        return new JobArea(box.world(), box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    private Land land(CompaniesApiImpl.Writes services, UUID owner, JobKind kind, JobArea area) {
        if (!kind.area() && area == NO_PLACE) return new Land.Found(JobLand.NONE, null);
        var world = Bukkit.getWorld(area.world());
        if (world == null) return new Land.Refused(JobProblem.NO_WORLD);
        if (area.minY() < world.getMinHeight() || area.maxY() >= world.getMaxHeight()) {
            return new Land.Refused(JobProblem.HEIGHT);
        }
        if (!kind.area()) return new Land.Found(JobLand.NONE, null);
        var unownedAllowed = services.jobSettings().get().unownedAllowed(area.world());
        return switch (services.jobs().hooks().decide(players.apply(owner), area, unownedAllowed)) {
            case JobHooks.Decision.Refused refused -> new Land.Refused(JobProblem.FOREIGN_LAND);
            case JobHooks.Decision.Unowned unowned -> new Land.Refused(JobProblem.UNOWNED_LAND);
            case JobHooks.Decision.Land found -> new Land.Found(found.land(), found.ref());
        };
    }

    private static PostJobResult post(
            CompaniesApiImpl.Writes services,
            JobRequest request,
            JobArea area,
            @Nullable Blueprint blueprint,
            Land land) {
        if (land instanceof Land.Refused(var problem)) return new PostJobResult.Rejected(problem);
        var found = (Land.Found) land;
        var jobs = services.jobs();
        var posted = jobs.service()
                .post(
                        request.owner(),
                        request.kind(),
                        request.title(),
                        request.price(),
                        request.dueHours(),
                        jobs.node(),
                        area,
                        found.land(),
                        found.ref(),
                        details(request, blueprint),
                        request.charge());
        return switch (posted) {
            case JobService.Posted.Done done -> new PostJobResult.Posted(new ApiJob(done.job()));
            case JobService.Posted.Refused refused -> new PostJobResult.Rejected(problem(refused.refusal()));
        };
    }

    private static JobService.Details details(JobRequest request, @Nullable Blueprint blueprint) {
        var height = request.height();
        var surface = request.surface();
        return new JobService.Details(
                height == null ? null : new JobTarget(height, surface == null ? null : surface.name()),
                request.unitPrice(),
                blueprint,
                request.description());
    }

    private static JobProblem problem(JobService.Refusal refusal) {
        return switch (refusal) {
            case DISABLED -> JobProblem.DISABLED;
            case TITLE -> JobProblem.TITLE;
            case DESCRIPTION -> JobProblem.DESCRIPTION;
            case PRICE -> JobProblem.PRICE;
            case DUE -> JobProblem.DUE;
            case VOLUME -> JobProblem.VOLUME;
            case CHUNKS -> JobProblem.CHUNKS;
            case OVERLAP -> JobProblem.OVERLAP;
            case TOO_MANY -> JobProblem.TOO_MANY;
            case FUNDS -> JobProblem.FUNDS;
            case TARGET -> JobProblem.TARGET;
            case UNIT_PRICE -> JobProblem.UNIT_PRICE;
            case STRANDED -> JobProblem.STRANDED;
            case KIND, STORAGE, BLUEPRINT -> JobProblem.FAILED;
        };
    }

    /**
     * What the land check said.
     */
    private sealed interface Land {
        /**
         * The land the job lies on.
         *
         * @param land whose land it is
         * @param ref  the plot or region, null for unowned land or a contract
         */
        record Found(JobLand land, @Nullable String ref) implements Land {}

        /**
         * The area may not hold a job.
         *
         * @param problem why
         */
        record Refused(JobProblem problem) implements Land {}
    }
}
