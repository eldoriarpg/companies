/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.jobs.DigCheck;
import de.eldoria.companies.jobs.JobService;
import de.eldoria.companies.jobs.JobServices;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.services.events.EventTimes;
import de.eldoria.companies.util.Permission.Admin;
import de.eldoria.companies.util.PlayerNames;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import net.kyori.adventure.text.event.ClickEvent;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.IntArg;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * {@code /companyadmin job}: the disputes about jobs.
 * <ul>
 *     <li>{@code disputes} lists the disputed jobs with both sides, the reason, the due time, the work counted and, on
 *     the server of an area, how far it is cleared;</li>
 *     <li>{@code settle <id> <percent>} settles a dispute: the company gets {@code percent} of the price, split among
 *     its members by their work, and the owner gets the rest back.</li>
 * </ul>
 */
@Subcommand("job")
@Permission(Admin.JOBS)
@PlayerOnly
public final class JobAdminCommand {
    private static final String PREFIX = "admin.job.";

    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public JobAdminCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Lists the disputed jobs.
     *
     * @param sender sender of the command
     */
    @Executes("disputes")
    public void disputes(CommandSender sender) {
        var admin = Senders.player(sender);
        run(admin, () -> jobs().data().disputed().stream().map(this::read).toList(), found -> {
            if (found.isEmpty()) {
                deps.messages().sendMessage(admin, PREFIX + "none");
                return;
            }
            deps.messages()
                    .sendMessage(admin, MenuText.create().text("<heading>").locale(PREFIX + "title"));
            for (var dispute : found) show(admin, dispute);
        });
    }

    /**
     * Settles a dispute.
     *
     * @param sender  sender of the command
     * @param id      id of the job
     * @param percent the percent of the price the company gets
     */
    @Executes("settle")
    public void settle(CommandSender sender, @IntArg(min = 0) int id, @IntArg(min = 0, max = 100) int percent) {
        var admin = Senders.player(sender);
        run(admin, () -> jobs().service().settle(admin.getName(), id, percent), change -> {
            var values = Values.number("id", id);
            if (change == JobService.Change.DONE) {
                deps.plugin()
                        .getLogger()
                        .info("%s settled the dispute about job %d with %d %% to the company"
                                .formatted(admin.getName(), id, percent));
                jobs().access().checkSoon();
                deps.messages().sendMessage(admin, PREFIX + "settled", values, Values.number("percent", percent));
                return;
            }
            var key =
                    switch (change) {
                        case UNKNOWN -> "unknown";
                        case NO_COMPANY -> "noCompany";
                        default -> "notDisputed";
                    };
            deps.messages().sendError(admin, PREFIX + key, values);
        });
    }

    private Dispute read(Job job) {
        var company = job.company();
        var name = company == null
                ? null
                : Objects.requireNonNull(deps.companyData()
                                .retrieveCompanyById(company)
                                .asFuture()
                                .join())
                        .map(SimpleCompany::name)
                        .orElse(null);
        return new Dispute(job, name, jobs().data().workOf(job.id()), progress(job));
    }

    /**
     * @param job a disputed job
     * @return how far its area is cleared, empty for a contract, an area of another server or a check that did not end
     *     within a few seconds
     */
    private Optional<DigCheck.Progress> progress(Job job) {
        if (!job.kind().area()) return Optional.empty();
        try {
            return jobs().progress()
                    .of(job, false)
                    .copy()
                    .orTimeout(10, TimeUnit.SECONDS)
                    .join();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private void show(Player admin, Dispute dispute) {
        var job = dispute.job();
        var review = job.review();
        var id = job.id();
        var owner = playerName(job.owner());
        var disputeBy = review.disputeBy();
        var openedBy = disputeBy == null ? "?" : playerName(disputeBy);
        var messages = deps.messages();
        messages.sendMessage(
                admin,
                PREFIX + "entry",
                Values.number("id", id),
                Values.text("job_title", job.title()),
                Values.money("price", deps.services().economy(), job.price()),
                Values.text("owner", owner),
                Values.text("company", Objects.requireNonNullElse(dispute.company(), "?")),
                Values.text("by", openedBy),
                Values.text("reason", Objects.requireNonNullElse(review.dispute(), "")));
        var due = job.due();
        if (due != null) {
            var key = due.isAfter(Objects.requireNonNullElse(review.disputedAt(), Times.now())) ? "due" : "overdue";
            messages.sendMessage(admin, PREFIX + key, Values.text("time", EventTimes.show(due, Times.zone())));
        }
        var description = job.description();
        if (description != null) {
            messages.sendMessage(admin, PREFIX + "description", Values.text("description", description));
        }
        if (job.kind().area()) {
            var total =
                    dispute.work().values().stream().mapToInt(Integer::intValue).sum();
            messages.sendMessage(
                    admin,
                    PREFIX + "work",
                    Values.text("world", job.area().world()),
                    Values.text("center", job.area().center()),
                    Values.number("blocks", total));
            dispute.progress()
                    .ifPresentOrElse(
                            progress -> messages.sendMessage(
                                    admin,
                                    PREFIX + "progress",
                                    Values.number("percent", progress.percent()),
                                    Values.number("cleared", progress.cleared()),
                                    Values.number("total", progress.total())),
                            () -> messages.sendMessage(admin, PREFIX + "noProgress"));
        }
        var buttons = MenuText.create();
        for (var percent : List.of(100, 50, 0)) {
            buttons.text(
                    "<act_" + percent + "><show>[" + percent + " %]</act_" + percent + "> ",
                    Values.click(
                            "act_" + percent,
                            ClickEvent.suggestCommand("/companyadmin job settle " + id + " " + percent)));
        }
        messages.sendMessage(admin, buttons);
    }

    private String playerName(UUID player) {
        return Objects.requireNonNullElse(PlayerNames.of(player), player.toString());
    }

    private JobServices jobs() {
        return deps.services().jobs();
    }

    private <T> void run(Player player, Supplier<T> query, Consumer<T> answer) {
        deps.requireReady();
        CompletableFuture.supplyAsync(query, Threading.VIRTUAL)
                .thenAccept(answer)
                .exceptionally(err -> {
                    if (err.getCause() instanceof CommandFailure failure) {
                        failure.sendTo(player, deps.messages());
                        return null;
                    }
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not read or settle a dispute", err);
                    deps.messages().sendErrorActionBar(player, PREFIX + "failed");
                    return null;
                });
    }

    /**
     * A disputed job as listed.
     *
     * @param job     the job
     * @param company the name of the company holding it, null when it is gone
     * @param work     the blocks each worker removed
     * @param progress how far the area is cleared, empty when it can not be read here
     */
    private record Dispute(
            Job job, @Nullable String company, Map<UUID, Integer> work, Optional<DigCheck.Progress> progress) {}
}
