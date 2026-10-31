/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.configuration.elements.jobs.JobSettings;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobKind;
import de.eldoria.companies.data.wrapper.job.JobLand;
import de.eldoria.companies.data.wrapper.job.JobReview;
import de.eldoria.companies.data.wrapper.job.JobState;
import de.eldoria.companies.jobs.JobService;
import de.eldoria.companies.jobs.JobServices;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The dashboard's Jobs page, as {@code /order job} and {@code /company job} offer it: the open jobs, the player's own
 * jobs and those of their company, a job's details, posting a contract, and the steps of a job's life that need no
 * world: describing, approving, rejecting, disputing, withdrawing a dispute, cancelling and ending a harvest for the
 * owner; taking, giving back, saying a contract is done and ending a harvest for the company. Area jobs are posted and
 * checked in game, as they need a selection and the world. The owner of a job is never named to companies, and the
 * company working on a job never to its owner. A contract posted here has no place to look at. The methods block on
 * the database and are called off the main thread.
 */
public final class JobWrites {
    /**
     * The actions performed here.
     */
    public static final Set<String> ACTIONS = Set.of(
            Actions.JOBS_LIST,
            Actions.JOBS_INFO,
            Actions.JOBS_CONTRACT,
            Actions.JOBS_DESCRIBE,
            Actions.JOBS_APPROVE,
            Actions.JOBS_REJECT,
            Actions.JOBS_DISPUTE,
            Actions.JOBS_WITHDRAW,
            Actions.JOBS_CANCEL,
            Actions.JOBS_END,
            Actions.JOBS_TAKE,
            Actions.JOBS_GIVE_BACK,
            Actions.JOBS_DONE,
            Actions.JOBS_EXTEND,
            Actions.JOBS_EXTENSION_ASK,
            Actions.JOBS_EXTENSION_GRANT,
            Actions.JOBS_EXTENSION_REFUSE);

    /**
     * The most jobs of each list.
     */
    static final int LISTED = 50;

    private static final String OWNER = "order.job.";
    private static final String COMPANY = "company.job.";

    private final Sources sources;
    private final Answers answers;

    /**
     * @param sources what jobs are read and changed with
     */
    public JobWrites(Sources sources) {
        this.sources = sources;
        answers = new Answers(sources.render());
    }

    /**
     * Performs an action of the Jobs page.
     *
     * @param request the action, one of {@link #ACTIONS}
     * @param session the player's session
     * @param args    the arguments
     * @return the result
     */
    ActionResult perform(ActionRequest request, StoredSession session, JsonNode args) {
        var jobs = sources.jobs().get();
        if (jobs == null) return answers.refused(request, "dashboard.unsupported");
        var player = session.player();
        var company = sources.companyOf().apply(player).join();
        var id = args.path("job").asInt(0);
        var service = jobs.service();
        return switch (request.name()) {
            case Actions.JOBS_LIST -> answers.data(request, list(jobs, session, company));
            case Actions.JOBS_INFO -> info(request, jobs, player, company, id);
            case Actions.JOBS_CONTRACT -> contract(request, jobs, session, args);
            case Actions.JOBS_DESCRIBE ->
                owner(
                        request,
                        jobs,
                        player,
                        company,
                        id,
                        service.describe(player, id, args.path("text").asString("")));
            case Actions.JOBS_APPROVE ->
                owner(request, jobs, player, company, id, service.approve(player, session.playerName(), id));
            case Actions.JOBS_REJECT ->
                owner(
                        request,
                        jobs,
                        player,
                        company,
                        id,
                        service.reject(player, id, args.path("reason").asString("")));
            case Actions.JOBS_EXTEND ->
                extension(
                        request,
                        jobs,
                        player,
                        company,
                        id,
                        service.extend(player, id, args.path("hours").asInt(0)));
            case Actions.JOBS_EXTENSION_GRANT ->
                extension(request, jobs, player, company, id, service.grantExtension(player, id));
            case Actions.JOBS_EXTENSION_REFUSE ->
                extension(
                        request,
                        jobs,
                        player,
                        company,
                        id,
                        service.refuseExtension(player, id, args.path("reason").asString("")));
            case Actions.JOBS_EXTENSION_ASK ->
                forCompany(
                        request,
                        jobs,
                        player,
                        company,
                        id,
                        c -> service.askExtension(
                                c,
                                player,
                                id,
                                args.path("hours").asInt(0),
                                args.path("reason").asString("")));
            case Actions.JOBS_CANCEL ->
                owner(request, jobs, player, company, id, service.cancel(player, session.playerName(), id));
            case Actions.JOBS_DISPUTE -> {
                var reason = args.path("reason").asString("");
                yield owns(jobs, player, id)
                        ? owner(request, jobs, player, company, id, service.disputeAsOwner(player, id, reason))
                        : forCompany(
                                request,
                                jobs,
                                player,
                                company,
                                id,
                                c -> service.disputeAsCompany(c, player, id, reason));
            }
            case Actions.JOBS_WITHDRAW ->
                owns(jobs, player, id)
                        ? owner(request, jobs, player, company, id, service.withdrawAsOwner(player, id))
                        : forCompany(request, jobs, player, company, id, c -> service.withdrawAsCompany(c, player, id));
            case Actions.JOBS_END -> {
                jobs.access().flush(id);
                yield owns(jobs, player, id)
                        ? owner(
                                request,
                                jobs,
                                player,
                                company,
                                id,
                                service.endAsOwner(player, session.playerName(), id))
                        : forCompany(request, jobs, player, company, id, c -> service.endAsCompany(c, player, id));
            }
            case Actions.JOBS_TAKE -> forCompany(request, jobs, player, company, id, c -> service.take(c, player, id));
            case Actions.JOBS_GIVE_BACK -> {
                var result = forCompany(request, jobs, player, company, id, c -> service.giveBack(c, player, id));
                if (result.ok()) jobs.access().forget(id);
                yield result;
            }
            case Actions.JOBS_DONE ->
                forCompany(request, jobs, player, company, id, c -> service.markDone(c, player, id));
            default -> answers.refused(request, "dashboard.unsupported");
        };
    }

    private JobList list(JobServices jobs, StoredSession session, Optional<CompanyProfile> company) {
        var player = session.player();
        var data = jobs.data();
        var open = data.open(sources.now().get(), LISTED).stream()
                .filter(job -> company.map(found -> found.member(job.owner()).isEmpty())
                        .orElse(true))
                .map(job -> row(job, player, company))
                .toList();
        var mine = data.ofOwner(player, LISTED).stream()
                .map(job -> row(job, player, company))
                .toList();
        var ours = company.map(found -> data.ofCompany(found.id()).stream()
                        .map(job -> row(job, player, company))
                        .toList())
                .orElse(List.of());
        return new JobList(
                open,
                mine,
                ours,
                company.isPresent(),
                canManage(company, player),
                session.permissions().contains(Permission.Orders.JOBS)
                        && sources.settings().get().enabled());
    }

    private ActionResult info(
            ActionRequest request, JobServices jobs, UUID player, Optional<CompanyProfile> company, int id) {
        var job = visible(jobs, player, company, id);
        if (job.isEmpty()) return answers.refused(request, CommandFailure.of(COMPANY + "error.unknown", number(id)));
        return answers.data(request, details(jobs, job.get(), player, company));
    }

    private ActionResult contract(ActionRequest request, JobServices jobs, StoredSession session, JsonNode args) {
        if (!session.permissions().contains(Permission.Orders.JOBS)) {
            return answers.refused(request, "error.permission.postJob");
        }
        var title = args.path("title").asString("");
        var description = args.path("description").asString("").strip();
        if (description.length() > Job.MAX_DESCRIPTION) {
            return answers.refused(
                    request, CommandFailure.of(OWNER + "error.description", Values.number("max", Job.MAX_DESCRIPTION)));
        }
        var posted = jobs.service()
                .post(
                        session.player(),
                        JobKind.CONTRACT,
                        title,
                        args.path("price").asDouble(0),
                        args.path("hours").asInt(0),
                        jobs.node(),
                        new JobArea("", 0, 0, 0, 0, 0, 0),
                        JobLand.NONE,
                        null,
                        JobService.Details.contract(description));
        return switch (posted) {
            case JobService.Posted.Done done ->
                answers.data(request, details(jobs, done.job(), session.player(), Optional.empty()));
            case JobService.Posted.Refused refused ->
                answers.refused(
                        request,
                        CommandFailure.of(
                                OWNER + "error." + camel(refused.refusal().name()),
                                Values.number(
                                        "max",
                                        maxOf(
                                                refused.refusal(),
                                                sources.settings().get()))));
        };
    }

    private ActionResult owner(
            ActionRequest request,
            JobServices jobs,
            UUID player,
            Optional<CompanyProfile> company,
            int id,
            JobService.Change change) {
        if (change == JobService.Change.DONE) {
            jobs.access().checkSoon();
            return jobs.data()
                    .job(id)
                    .map(job -> answers.data(request, details(jobs, job, player, company)))
                    .orElseGet(() -> answers.refused(request, CommandFailure.of(OWNER + "error.unknown", number(id))));
        }
        var key =
                switch (change) {
                    case UNKNOWN, NOT_YOURS -> "error.unknown";
                    case TAKEN -> "error.taken";
                    case DISPUTED -> "error.disputed";
                    case REASON -> "error.reason";
                    case TOO_LONG -> "error.description";
                    case NOT_DONE -> "error.notDone";
                    case HARVEST -> "error.harvest";
                    default -> "error.closed";
                };
        var max = change == JobService.Change.TOO_LONG ? Job.MAX_DESCRIPTION : JobReview.MAX_REASON;
        return answers.refused(request, CommandFailure.of(OWNER + key, number(id), Values.number("max", max)));
    }

    /**
     * Answers an owner's extension of a job: the job on success, otherwise the refusal worded for extensions, as the
     * in-game command words it.
     */
    private ActionResult extension(
            ActionRequest request,
            JobServices jobs,
            UUID player,
            Optional<CompanyProfile> company,
            int id,
            JobService.Change change) {
        if (change == JobService.Change.DONE) return owner(request, jobs, player, company, id, change);
        var key =
                switch (change) {
                    case UNKNOWN, NOT_YOURS -> "error.unknown";
                    case DISABLED -> "error.extensionsOff";
                    case HOURS -> "error.extensionHours";
                    case NO_REQUEST -> "error.noExtensionRequest";
                    case REASON -> "error.extensionReason";
                    default -> "error.extensionClosed";
                };
        return answers.refused(
                request,
                CommandFailure.of(
                        OWNER + key,
                        number(id),
                        Values.number("max", JobReview.MAX_REASON),
                        Values.number("left", change == JobService.Change.HOURS ? left(jobs, id) : 0)));
    }

    private ActionResult forCompany(
            ActionRequest request,
            JobServices jobs,
            UUID player,
            Optional<CompanyProfile> company,
            int id,
            Function<CompanyProfile, JobService.Change> change) {
        if (company.isEmpty()) return answers.refused(request, COMPANY + "error.noCompany");
        var result = change.apply(company.get());
        if (result == JobService.Change.DONE) {
            jobs.access().checkSoon();
            var refreshed = sources.companyOf().apply(player).join();
            return jobs.data()
                    .job(id)
                    .map(job -> answers.data(request, details(jobs, job, player, refreshed)))
                    .orElseGet(
                            () -> answers.refused(request, CommandFailure.of(COMPANY + "error.unknown", number(id))));
        }
        var key =
                switch (result) {
                    case NO_PERMISSION -> "error.permission";
                    case UNKNOWN -> "error.unknown";
                    case LIMIT -> "error.limit";
                    case OWN -> "error.own";
                    case NOT_YOURS -> "error.notYours";
                    case DISPUTED -> "error.disputed";
                    case REASON -> "error.reason";
                    case HARVEST -> "error.harvest";
                    case AREA -> "error.inGame";
                    case DISABLED -> "error.extensionsOff";
                    case HOURS -> "error.extensionHours";
                    case ASKED -> "error.extensionAsked";
                    case REQUESTS -> "error.extensionRequests";
                    default -> "error.closed";
                };
        return answers.refused(
                request,
                CommandFailure.of(
                        COMPANY + key,
                        number(id),
                        Values.number("length", JobReview.MAX_REASON),
                        Values.number("left", result == JobService.Change.HOURS ? left(jobs, id) : 0),
                        Values.number("max", result == JobService.Change.REQUESTS ? requests(jobs, id) : 0)));
    }

    private static int left(JobServices jobs, int id) {
        return jobs.data().job(id).map(job -> jobs.service().hoursLeft(job)).orElse(0);
    }

    private static int requests(JobServices jobs, int id) {
        return jobs.data().job(id).map(job -> job.extension().requests()).orElse(0);
    }

    private static boolean owns(JobServices jobs, UUID player, int id) {
        return jobs.data().job(id).map(job -> job.owner().equals(player)).orElse(false);
    }

    /**
     * A job the player may open: an open one, one of their own, or one their company holds.
     */
    private static Optional<Job> visible(JobServices jobs, UUID player, Optional<CompanyProfile> company, int id) {
        if (id <= 0) return Optional.empty();
        return jobs.data()
                .job(id)
                .filter(job -> job.state() == JobState.OPEN
                        || job.owner().equals(player)
                        || company.map(found -> Integer.valueOf(found.id()).equals(job.company()))
                                .orElse(false));
    }

    private JobRow row(Job job, UUID player, Optional<CompanyProfile> company) {
        var harvest = job.harvest();
        var area = job.area();
        return new JobRow(
                job.id(),
                job.kind().name().toLowerCase(Locale.ROOT),
                job.state().name().toLowerCase(Locale.ROOT),
                job.title(),
                job.price(),
                job.fee(),
                instant(job.due()),
                instant(job.expires()),
                job.kind().area() ? area.volume() : 0,
                job.kind().area() ? area.world() : null,
                job.kind().area() ? area.center() : null,
                harvest == null ? null : harvest.unitPrice(),
                harvest == null ? null : harvest.paid(),
                job.owner().equals(player),
                company.map(found -> Integer.valueOf(found.id()).equals(job.company()))
                        .orElse(false));
    }

    private JobDetails details(JobServices jobs, Job job, UUID player, Optional<CompanyProfile> company) {
        var review = job.review();
        var mine = job.owner().equals(player);
        var target = job.target();
        var doneAt = review.doneAt();
        var disputeBy = review.disputeBy();
        Map<String, Integer> stock =
                mine && job.kind() == JobKind.HARVEST ? jobs.data().stock(job.id()) : Map.of();
        var settings = sources.settings().get();
        var extension = job.extension();
        var asked = job.state() == JobState.TAKEN ? extension.askedHours() : null;
        var extendable = settings.extensionsEnabled() && job.state() == JobState.TAKEN;
        return new JobDetails(
                row(job, player, company),
                job.description(),
                review.rejected(),
                review.dispute(),
                disputeBy != null && disputeBy.equals(job.owner()),
                review.settled(),
                doneAt == null
                        ? null
                        : instant(doneAt.plusHours(sources.settings().get().approveHours())),
                target == null ? null : target.y(),
                target == null ? null : target.surface(),
                stock,
                canManage(company, player),
                job.state() == JobState.OPEN
                        && !mine
                        && company.map(found -> found.member(job.owner()).isEmpty())
                                .orElse(false),
                extension.extendedHours(),
                extendable ? jobs.service().hoursLeft(job) : 0,
                extendable ? Math.max(0, settings.maxExtensionRequests() - extension.requests()) : 0,
                asked == null
                        ? null
                        : new ExtensionRequest(
                                asked,
                                Objects.requireNonNullElse(extension.reason(), ""),
                                instant(extension.askedAt())));
    }

    private @Nullable Instant instant(@Nullable LocalDateTime time) {
        return time == null ? null : sources.instant().apply(time);
    }

    private static boolean canManage(Optional<CompanyProfile> company, UUID player) {
        return company.flatMap(found -> found.member(player))
                .map(member -> member.hasPermission(CompanyPermission.MANAGE_ORDERS))
                .orElse(false);
    }

    private static TagResolver.Single number(int id) {
        return Values.number("id", id);
    }

    private static Number maxOf(JobService.Refusal refusal, JobSettings settings) {
        return switch (refusal) {
            case TOO_MANY -> settings.maxOpenPerOwner();
            case DUE -> settings.maxDueHours();
            case TITLE -> Job.MAX_TITLE;
            case DESCRIPTION -> Job.MAX_DESCRIPTION;
            default -> 0;
        };
    }

    private static String camel(String name) {
        var parts = name.toLowerCase(Locale.ROOT).split("_");
        var key = new StringBuilder(parts[0]);
        for (var i = 1; i < parts.length; i++) {
            key.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return key.toString();
    }

    /**
     * What the Jobs page is built with.
     *
     * @param jobs      the jobs of this server, null while they are not running
     * @param companyOf the company of a player
     * @param settings  the settings of jobs
     * @param instant   turns a stored time into an instant
     * @param now       the current time
     * @param render    renders a refusal as plain text
     */
    public record Sources(
            Supplier<@Nullable JobServices> jobs,
            Function<UUID, CompletableFuture<Optional<CompanyProfile>>> companyOf,
            Supplier<JobSettings> settings,
            Function<LocalDateTime, Instant> instant,
            Supplier<LocalDateTime> now,
            Function<CommandFailure, String> render) {}

    /**
     * A job as listed.
     *
     * @param id        id of the job
     * @param kind      its kind, lower case
     * @param state     its state, lower case
     * @param title     its title
     * @param price     its price, the budget of a harvest job
     * @param fee       the fee its owner paid
     * @param due       when the company's time runs out, null while nobody holds it
     * @param expires   when it expires while nobody takes it
     * @param blocks    the blocks of its area, 0 for a contract
     * @param world     the world of its area, null for a contract
     * @param center    the center of its area, null for a contract
     * @param unitPrice what a harvest job pays per item, null for other kinds
     * @param paid      what a harvest job paid so far, null for other kinds
     * @param mine      whether the player owns it
     * @param ours      whether the player's company holds it
     */
    public record JobRow(
            int id,
            String kind,
            String state,
            String title,
            double price,
            double fee,
            @Nullable Instant due,
            @Nullable Instant expires,
            long blocks,
            @Nullable String world,
            @Nullable String center,
            @Nullable Double unitPrice,
            @Nullable Double paid,
            boolean mine,
            boolean ours) {}

    /**
     * The lists of the Jobs page.
     *
     * @param open      jobs waiting for a company, without those of the player's own company members
     * @param mine      the player's own jobs, the newest first
     * @param company   the jobs the player's company holds
     * @param inCompany whether the player belongs to a company
     * @param canManage whether the player may take and finish jobs for their company
     * @param canPost   whether the player may post jobs
     */
    public record JobList(
            List<JobRow> open,
            List<JobRow> mine,
            List<JobRow> company,
            boolean inCompany,
            boolean canManage,
            boolean canPost) {}

    /**
     * A job with what its page shows.
     *
     * @param job            the job
     * @param description    what the owner wrote about it
     * @param rejected       why the owner rejected the work last
     * @param dispute        why a dispute was opened
     * @param disputeByOwner whether the owner opened the dispute
     * @param settled        the percent an admin gave the company
     * @param approveAt      when a contract the company says is done is approved without an answer
     * @param targetY        the target height of a terraform job
     * @param surface        the surface block of a terraform job
     * @param stock          the stock of the player's harvest job, as amounts by material name
     * @param canManage      whether the player may take and finish jobs for their company
     * @param takeable       whether the player's company may take the job: it is open and owned by nobody of the company
     */
    public record JobDetails(
            JobRow job,
            @Nullable String description,
            @Nullable String rejected,
            @Nullable String dispute,
            boolean disputeByOwner,
            @Nullable Integer settled,
            @Nullable Instant approveAt,
            @Nullable Integer targetY,
            @Nullable String surface,
            Map<String, Integer> stock,
            boolean canManage,
            boolean takeable,
            int extendedHours,
            int hoursLeft,
            int requestsLeft,
            @Nullable ExtensionRequest request) {}

    /**
     * The open request of the company working on a job for more time.
     *
     * @param hours  the hours it asks for
     * @param reason why
     * @param asked  when it asked
     */
    public record ExtensionRequest(
            int hours, String reason, @Nullable Instant asked) {}
}
