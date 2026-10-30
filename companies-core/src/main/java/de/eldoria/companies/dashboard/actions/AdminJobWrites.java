/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.jobs.JobServices;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.AdminLog;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import org.jetbrains.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * The admin tools for disputes about jobs, as {@code /companyadmin job} offers them: the disputed jobs with both sides,
 * the reason, the due time and the work counted, and settling one with any split of its price. Settling is written to
 * the admin log. Needs {@code companies.admin.jobs}. The methods block on the database and are called off the main
 * thread.
 */
public final class AdminJobWrites {
    /**
     * The actions performed here.
     */
    public static final Set<String> ACTIONS = Set.of(Actions.ADMIN_JOBS_DISPUTES, Actions.ADMIN_JOBS_SETTLE);

    private static final String PREFIX = "admin.job.";

    private final Sources sources;
    private final Answers answers;

    /**
     * @param sources what disputes are read and settled with
     */
    public AdminJobWrites(Sources sources) {
        this.sources = sources;
        answers = new Answers(sources.render());
    }

    /**
     * Performs an admin action on disputes.
     *
     * @param request the action, one of {@link #ACTIONS}
     * @param session the admin's session
     * @param args    the arguments
     * @return the result
     */
    ActionResult perform(ActionRequest request, StoredSession session, JsonNode args) {
        if (!session.permissions().contains(Permission.Admin.JOBS)) {
            return answers.refused(request, "companyadmin.error.permission");
        }
        var jobs = sources.jobs().get();
        if (jobs == null) return answers.refused(request, "dashboard.unsupported");
        return switch (request.name()) {
            case Actions.ADMIN_JOBS_DISPUTES -> answers.data(request, new DisputeList(disputes(jobs)));
            case Actions.ADMIN_JOBS_SETTLE ->
                settle(
                        request,
                        jobs,
                        session,
                        args.path("job").asInt(0),
                        args.path("percent").asInt(-1));
            default -> answers.refused(request, "dashboard.unsupported");
        };
    }

    private List<DisputeRow> disputes(JobServices jobs) {
        return jobs.data().disputed().stream().map(job -> row(jobs, job)).toList();
    }

    private ActionResult settle(ActionRequest request, JobServices jobs, StoredSession session, int id, int percent) {
        var number = Values.number("id", id);
        if (percent < 0 || percent > 100) return answers.refused(request, "dashboard.invalid");
        var admin = session.playerName() + "@dashboard";
        var change = jobs.service().settle(admin, id, percent);
        return switch (change) {
            case DONE -> {
                sources.log()
                        .entry(admin, "job settle")
                        .with("id", id)
                        .with("percent", percent)
                        .write();
                jobs.access().checkSoon();
                yield answers.data(request, new DisputeList(disputes(jobs)));
            }
            case UNKNOWN -> answers.refused(request, CommandFailure.of(PREFIX + "unknown", number));
            case NO_COMPANY -> answers.refused(request, CommandFailure.of(PREFIX + "noCompany", number));
            default -> answers.refused(request, CommandFailure.of(PREFIX + "notDisputed", number));
        };
    }

    private DisputeRow row(JobServices jobs, Job job) {
        var review = job.review();
        var company = job.company();
        var disputeBy = review.disputeBy();
        var due = job.due();
        var disputedAt = review.disputedAt();
        var work = jobs.data().workOf(job.id()).values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        return new DisputeRow(
                job.id(),
                job.kind().name().toLowerCase(Locale.ROOT),
                job.title(),
                job.description(),
                job.price(),
                sources.names().apply(job.owner()),
                company == null ? null : sources.companyName().apply(company).orElse(null),
                disputeBy == null ? null : sources.names().apply(disputeBy),
                disputeBy != null && disputeBy.equals(job.owner()),
                review.dispute(),
                instant(due),
                instant(disputedAt),
                due != null && disputedAt != null && !due.isAfter(disputedAt),
                job.kind().area() ? job.area().world() : null,
                job.kind().area() ? job.area().center() : null,
                work);
    }

    private @Nullable Instant instant(@Nullable LocalDateTime time) {
        return time == null ? null : sources.instant().apply(time);
    }

    /**
     * What the dispute tools are built with.
     *
     * @param jobs        the jobs of this server, null while they are not running
     * @param names       the name of a player
     * @param companyName the name of a company by its id, empty when it is gone
     * @param instant     turns a stored time into an instant
     * @param log         the admin log
     * @param render      renders a refusal as plain text
     */
    public record Sources(
            Supplier<@Nullable JobServices> jobs,
            Function<UUID, String> names,
            IntFunction<Optional<String>> companyName,
            Function<LocalDateTime, Instant> instant,
            AdminLog log,
            Function<CommandFailure, String> render) {}

    /**
     * A disputed job.
     *
     * @param id          id of the job
     * @param kind        its kind, lower case
     * @param title       its title
     * @param description what its owner wrote about it
     * @param price       its price
     * @param owner       the name of its owner
     * @param company     the name of the company holding it, null when it is gone
     * @param openedBy    the name of who opened the dispute
     * @param byOwner     whether the owner opened it
     * @param reason      why
     * @param due         when the company's time ran until
     * @param disputedAt  when the dispute was opened
     * @param overdue     whether the company's time had run out when the dispute was opened
     * @param world       the world of its area, null for a contract
     * @param center      the center of its area, null for a contract
     * @param work        the blocks counted as work
     */
    public record DisputeRow(
            int id,
            String kind,
            String title,
            @Nullable String description,
            double price,
            String owner,
            @Nullable String company,
            @Nullable String openedBy,
            boolean byOwner,
            @Nullable String reason,
            @Nullable Instant due,
            @Nullable Instant disputedAt,
            boolean overdue,
            @Nullable String world,
            @Nullable String center,
            int work) {}

    /**
     * The disputed jobs, the oldest dispute first.
     *
     * @param disputes the disputes
     */
    public record DisputeList(List<DisputeRow> disputes) {}
}
