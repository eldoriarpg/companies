/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.api.write.JobChange;
import de.eldoria.companies.api.write.JobProblem;
import de.eldoria.companies.api.write.JobRequest;
import de.eldoria.companies.api.write.PostJobResult;
import de.eldoria.companies.components.job.BuildBlock;
import de.eldoria.companies.components.job.IJob;
import de.eldoria.companies.components.job.IJobArea;
import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AJobData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
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
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Reads jobs from the repository and posts them through the job service. The world and the land plugins are asked on
 * the main thread, as {@code /order job} does; the service runs off it.
 */
final class JobApiImpl implements JobApi {
    private static final JobArea NO_PLACE = new JobArea("", 0, 0, 0, 0, 0, 0);

    private final AJobData jobs;
    private final ACompanyData companies;
    private final WriteAccess writes;
    private final Function<UUID, OfflinePlayer> players;
    private final Executor executor;

    JobApiImpl(
            AJobData jobs,
            ACompanyData companies,
            WriteAccess writes,
            Function<UUID, OfflinePlayer> players,
            Executor executor) {
        this.jobs = jobs;
        this.companies = companies;
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

    @Override
    public CompletableFuture<Optional<List<BuildBlock>>> blueprint(int id) {
        // TODO: hand out the blueprint's water and lava sources as well
        return CompletableFuture.supplyAsync(
                () -> jobs.job(id)
                        .filter(job -> job.kind() == JobKind.BUILD)
                        .flatMap(job -> jobs.blueprint(id))
                        .map(data -> Blueprint.decode(data).cells().stream()
                                .map(cell -> new BuildBlock(cell.x(), cell.y(), cell.z(), cell.type(), blockData(cell)))
                                .toList()),
                executor);
    }

    @Override
    public CompletableFuture<JobChange> take(int id, UUID member) {
        return asMember(member, (services, company) -> {
            var change = services.jobs().service().take(company, member, id);
            if (change == JobService.Change.DONE) services.jobs().access().checkSoon();
            return change(change);
        });
    }

    @Override
    public CompletableFuture<JobChange> giveBack(int id, UUID member) {
        return asMember(member, (services, company) -> {
            var change = services.jobs().service().giveBack(company, member, id);
            if (change == JobService.Change.DONE) {
                services.jobs().access().forget(id);
                services.jobs().access().checkSoon();
            }
            return change(change);
        });
    }

    @Override
    public CompletableFuture<JobChange> cancel(int id, UUID owner) {
        return asOwner(services -> {
            var change = services.jobs().service().cancel(owner, name(owner), id);
            if (change == JobService.Change.DONE) services.jobs().access().checkSoon();
            return change == JobService.Change.NOT_YOURS ? JobService.Change.UNKNOWN : change;
        });
    }

    @Override
    public CompletableFuture<JobChange> answerCancel(int id, UUID member, boolean agree) {
        return asMember(member, (services, company) -> {
            var change = services.jobs().service().answerCancel(company, member, name(member), id, agree);
            if (change == JobService.Change.DONE && agree)
                services.jobs().access().checkSoon();
            return change(change);
        });
    }

    @Override
    public CompletableFuture<JobChange> finish(int id, UUID member) {
        return asMember(member, (services, company) -> {
            var finished = services.jobs().finish(company, member, id);
            return switch (finished) {
                case JobService.Finished.Paid paid -> {
                    services.jobs().access().checkSoon();
                    yield JobChange.DONE;
                }
                case JobService.Finished.Ended ended -> {
                    services.jobs().access().checkSoon();
                    yield JobChange.DONE;
                }
                case JobService.Finished.Waiting waiting -> JobChange.DONE;
                case JobService.Finished.NotDone notDone -> JobChange.NOT_DONE;
                case JobService.Finished.Refused refused -> change(refused.change());
            };
        });
    }

    @Override
    public CompletableFuture<JobChange> approve(int id, UUID owner) {
        return asOwner(services -> {
            services.jobs().access().flush(id);
            var change = services.jobs().service().approve(owner, name(owner), id);
            if (change == JobService.Change.DONE) services.jobs().access().checkSoon();
            return change;
        });
    }

    @Override
    public CompletableFuture<JobChange> reject(int id, UUID owner, String reason) {
        Objects.requireNonNull(reason, "reason");
        return asOwner(services -> services.jobs().service().reject(owner, id, reason));
    }

    private CompletableFuture<JobChange> asMember(
            UUID member, BiFunction<CompaniesApiImpl.Writes, CompanyProfile, JobChange> change) {
        return writes.run(services -> CompletableFuture.supplyAsync(
                () -> {
                    var company = companies
                            .retrievePlayerCompanyProfile(players.apply(member))
                            .asFuture()
                            .join();
                    if (company == null || company.isEmpty()) return JobChange.NO_COMPANY;
                    return change.apply(services, company.get());
                },
                executor));
    }

    private CompletableFuture<JobChange> asOwner(Function<CompaniesApiImpl.Writes, JobService.Change> change) {
        return writes.run(services -> CompletableFuture.supplyAsync(() -> change(change.apply(services)), executor));
    }

    private String name(UUID player) {
        var name = players.apply(player).getName();
        return name == null ? player.toString() : name;
    }

    /**
     * @param change what came of a change in the job service
     * @return the same as the API names it
     */
    static JobChange change(JobService.Change change) {
        return switch (change) {
            case DONE -> JobChange.DONE;
            case UNKNOWN -> JobChange.UNKNOWN;
            case NO_PERMISSION -> JobChange.NO_PERMISSION;
            case NO_COMPANY -> JobChange.NO_COMPANY;
            case LIMIT -> JobChange.LIMIT;
            case OWN -> JobChange.OWN;
            case NOT_YOURS -> JobChange.NOT_YOURS;
            case DISPUTED -> JobChange.DISPUTED;
            case REASON, TOO_LONG -> JobChange.REASON;
            case AREA, CONTRACT, HARVEST, DRAFT -> JobChange.WRONG_KIND;
            case NOT_DONE -> JobChange.NOT_DONE;
            case OTHER_SERVER -> JobChange.OTHER_SERVER;
            case REFUSED -> JobChange.REFUSED;
            case WORLD -> JobChange.NOT_LOADED;
            case CANCEL_ASKED -> JobChange.CANCEL_ASKED;
            case ASKED, NO_REQUEST -> JobChange.PENDING;
            case CLOSED, TAKEN, DISABLED, HOURS, REQUESTS -> JobChange.CLOSED;
        };
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
                .map(block -> {
                    var data = block.data();
                    return data == null
                            ? new Blueprint.Cell(block.x(), block.y(), block.z(), block.type())
                            : new Blueprint.Cell(block.x(), block.y(), block.z(), block.type(), data.getAsString());
                })
                .toList());
    }

    private static @Nullable BlockData blockData(Blueprint.Cell cell) {
        if (cell.data().indexOf('[') < 0) return null;
        try {
            return Bukkit.createBlockData(cell.data());
        } catch (RuntimeException e) {
            return null;
        }
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
            case REFUSED -> JobProblem.REFUSED;
            case KIND, STORAGE, BLUEPRINT, DRAFTS, NO_DRAFT -> JobProblem.FAILED;
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
