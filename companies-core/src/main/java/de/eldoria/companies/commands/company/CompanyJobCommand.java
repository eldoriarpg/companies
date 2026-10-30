/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobState;
import de.eldoria.companies.jobs.DigCheck;
import de.eldoria.companies.jobs.JobService;
import de.eldoria.companies.jobs.JobServices;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Texts;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.IntArg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * {@code /company job}: the jobs companies take like orders, work in an area of a player's land.
 * <ul>
 *     <li>{@code /company job} lists the open jobs;</li>
 *     <li>{@code mine} lists the jobs the company works on;</li>
 *     <li>{@code info <id>} shows a job, and for one of the company on the server of its area how far the work
 *     is;</li>
 *     <li>{@code take <id>} takes an open job, with {@code manage_orders}, using one of the company's order slots;</li>
 *     <li>{@code giveback <id>} gives a job back; it is open again;</li>
 *     <li>{@code done <id>} checks the job's area on the server that holds it and, when the work is done, pays the
 *     members by the blocks each removed.</li>
 * </ul>
 * The owner of a job is never named.
 */
@Subcommand("job")
@PlayerOnly
public final class CompanyJobCommand {
    private static final String PREFIX = "company.job.";
    private static final int LISTED = 20;

    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public CompanyJobCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Lists the open jobs.
     *
     * @param sender sender of the command
     */
    @Executes
    public void list(CommandSender sender) {
        var player = Senders.player(sender);
        run(player, () -> jobs().data().open(Times.now(), LISTED), found -> listing(player, found, "title", "none"));
    }

    /**
     * Lists the jobs the player's company works on.
     *
     * @param sender sender of the command
     */
    @Executes("mine")
    public void mine(CommandSender sender) {
        var player = Senders.player(sender);
        run(
                player,
                () -> jobs().data().ofCompany(company(player).id()),
                found -> listing(player, found, "mineTitle", "noneMine"));
    }

    /**
     * Shows a job.
     *
     * @param sender sender of the command
     * @param id     id of the job
     */
    @Executes("info")
    public void info(CommandSender sender, @IntArg(min = 0) int id) {
        var player = Senders.player(sender);
        run(
                player,
                () -> {
                    var job = jobs().data().job(id).orElseThrow(() -> unknown(id));
                    var ours = companyOf(player)
                            .filter(company -> Integer.valueOf(company.id()).equals(job.company()))
                            .isPresent();
                    if (job.state() != JobState.OPEN && !ours) throw unknown(id);
                    return new Shown(
                            job, ours && job.state() == JobState.TAKEN ? progress(job, false) : Optional.empty());
                },
                shown -> show(player, shown));
    }

    /**
     * Takes an open job for the player's company.
     *
     * @param sender sender of the command
     * @param id     id of the job
     */
    @Executes("take")
    public void take(CommandSender sender, @IntArg(min = 0) int id) {
        var player = Senders.player(sender);
        run(player, () -> jobs().service().take(company(player), player.getUniqueId(), id), change -> {
            if (change == JobService.Change.DONE) {
                jobs().access().checkSoon();
                deps.messages()
                        .sendMessage(
                                player,
                                MenuText.create()
                                        .locale(PREFIX + "taken", Values.number("id", id))
                                        .text(
                                                " <act_info><show>[<i18n:words.info>]</act_info>",
                                                Values.click(
                                                        "act_info", ClickEvent.runCommand("/company job info " + id))));
                return;
            }
            refused(player, id, change);
        });
    }

    /**
     * Gives a job of the player's company back.
     *
     * @param sender sender of the command
     * @param id     id of the job
     */
    @Executes("giveback")
    public void giveBack(CommandSender sender, @IntArg(min = 0) int id) {
        var player = Senders.player(sender);
        run(player, () -> jobs().service().giveBack(company(player), player.getUniqueId(), id), change -> {
            if (change == JobService.Change.DONE) {
                jobs().access().forget(id);
                jobs().access().checkSoon();
                deps.messages().sendMessage(player, PREFIX + "givenBack", Values.number("id", id));
                return;
            }
            refused(player, id, change);
        });
    }

    /**
     * Checks a job of the player's company and pays it when the work is done.
     *
     * @param sender sender of the command
     * @param id     id of the job
     */
    @Executes("done")
    public void done(CommandSender sender, @IntArg(min = 0) int id) {
        var player = Senders.player(sender);
        run(
                player,
                () -> {
                    var company = company(player);
                    var may = jobs().service().mayFinish(company, player.getUniqueId(), id);
                    if (may != JobService.Change.DONE) return new JobService.Finished.Refused(may);
                    var job = jobs().data().job(id).orElseThrow(() -> unknown(id));
                    if (!job.node().equals(jobs().node())) {
                        throw CommandFailure.of(PREFIX + "error.otherServer", Values.number("id", id));
                    }
                    jobs().access().flush(id);
                    var progress = progress(job, true)
                            .orElseThrow(() -> CommandFailure.of(PREFIX + "error.world", Values.number("id", id)));
                    return jobs().service().finish(company, player.getUniqueId(), id, progress);
                },
                finished -> {
                    switch (finished) {
                        case JobService.Finished.Paid paid -> {
                            jobs().access().checkSoon();
                            deps.messages()
                                    .sendMessage(
                                            player,
                                            PREFIX + "paid",
                                            Values.number("id", id),
                                            Values.money("paid", deps.services().economy(), paid.paid()));
                        }
                        case JobService.Finished.NotDone notDone ->
                            deps.messages()
                                    .sendError(
                                            player,
                                            PREFIX + "notDone",
                                            Values.number(
                                                    "percent",
                                                    notDone.progress().percent()),
                                            Values.text(
                                                    "block", notDone.progress().leftText()));
                        case JobService.Finished.Refused refused -> refused(player, id, refused.change());
                    }
                });
    }

    private void listing(Player player, List<Job> found, String title, String none) {
        if (found.isEmpty()) {
            deps.messages().sendMessage(player, PREFIX + none);
            return;
        }
        deps.messages().sendMessage(player, MenuText.create().text("<heading>").locale(PREFIX + title));
        for (var job : found) deps.messages().sendMessage(player, entry(player, job));
    }

    private MenuText entry(Player player, Job job) {
        var id = job.id();
        var time = job.state() == JobState.TAKEN ? job.due() : job.expires();
        return MenuText.create()
                .locale(
                        PREFIX + "entry",
                        Values.number("id", id),
                        Values.text("job_title", job.title()),
                        Values.component("kind", kind(player, job)),
                        Values.money("price", deps.services().economy(), job.price()),
                        Values.number("blocks", job.area().volume()),
                        Values.text("world", job.area().world()),
                        Values.text("time", left(time)))
                .text(
                        " <act_info><show>[<i18n:words.info>]</act_info>",
                        Values.click("act_info", ClickEvent.runCommand("/company job info " + id)));
    }

    private void show(Player player, Shown shown) {
        var job = shown.job();
        var messages = deps.messages();
        messages.sendMessage(
                player,
                PREFIX + "info.head",
                Values.number("id", job.id()),
                Values.text("job_title", job.title()),
                Values.component("kind", kind(player, job)));
        messages.sendMessage(
                player,
                PREFIX + "info.area",
                Values.text("world", job.area().world()),
                Values.text(
                        "from",
                        job.area().minX() + " " + job.area().minY() + " "
                                + job.area().minZ()),
                Values.text(
                        "to",
                        job.area().maxX() + " " + job.area().maxY() + " "
                                + job.area().maxZ()),
                Values.number("blocks", job.area().volume()));
        messages.sendMessage(
                player,
                PREFIX + "info.money",
                Values.money("price", deps.services().economy(), job.price()),
                Values.number("hours", job.dueHours()));
        if (job.state() == JobState.TAKEN) {
            messages.sendMessage(player, PREFIX + "info.due", Values.text("time", left(job.due())));
        }
        shown.progress()
                .ifPresent(progress -> messages.sendMessage(
                        player,
                        PREFIX + "info.progress",
                        Values.number("percent", progress.percent()),
                        Values.number("cleared", progress.cleared()),
                        Values.number("total", progress.total())));
        var id = job.id();
        var buttons = MenuText.create();
        if (job.state() == JobState.OPEN) {
            buttons.text(
                    "<act_take><add>[<i18n:" + PREFIX + "takeButton>]</act_take>",
                    Values.click("act_take", ClickEvent.runCommand("/company job take " + id)));
        } else if (job.state() == JobState.TAKEN) {
            buttons.text(
                            "<act_done><add>[<i18n:" + PREFIX + "doneButton>]</act_done> ",
                            Values.click("act_done", ClickEvent.runCommand("/company job done " + id)))
                    .text(
                            "<act_back><remove>[<i18n:" + PREFIX + "giveBackButton>]</act_back>",
                            Values.click("act_back", ClickEvent.suggestCommand("/company job giveback " + id)));
        }
        messages.sendMessage(player, buttons);
    }

    private void refused(Player player, int id, JobService.Change change) {
        var values = Values.number("id", id);
        var key =
                switch (change) {
                    case NO_PERMISSION -> "error.permission";
                    case UNKNOWN -> "error.unknown";
                    case CLOSED -> "error.closed";
                    case LIMIT -> "error.limit";
                    case OWN -> "error.own";
                    case NOT_YOURS -> "error.notYours";
                    case DONE, TAKEN -> "error.closed";
                };
        deps.messages().sendError(player, PREFIX + key, values);
    }

    /**
     * Reads how far a job is on the server of its area.
     *
     * @param job   the job
     * @param fresh whether to check the area anew
     * @return the progress, empty for a job of another server or a world not loaded
     */
    private Optional<DigCheck.Progress> progress(Job job, boolean fresh) {
        return jobs().progress().of(job, fresh).join();
    }

    private Component kind(Player player, Job job) {
        return new MenuComponents(deps.messages(), player)
                .locale("jobs.kind." + job.kind().name().toLowerCase(Locale.ROOT));
    }

    private static String left(@Nullable LocalDateTime until) {
        if (until == null) return Texts.prettyDuration(Duration.ZERO);
        var duration = Duration.between(Times.now(), until);
        return Texts.prettyDuration(duration.isNegative() ? Duration.ZERO : duration);
    }

    private static CommandFailure unknown(int id) {
        return CommandFailure.of(PREFIX + "error.unknown", Values.number("id", id));
    }

    private CompanyProfile company(Player player) {
        return companyOf(player).orElseThrow(() -> CommandFailure.of(PREFIX + "error.noCompany"));
    }

    private Optional<CompanyProfile> companyOf(Player player) {
        var found = deps.companyData()
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .join();
        return found == null ? Optional.empty() : found;
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
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not read or change a job", err);
                    deps.messages().sendErrorActionBar(player, PREFIX + "error.failed");
                    return null;
                });
    }

    /**
     * A job as shown, with how far the work is when the company works on it on this server.
     *
     * @param job      the job
     * @param progress the progress
     */
    private record Shown(Job job, Optional<DigCheck.Progress> progress) {}
}
