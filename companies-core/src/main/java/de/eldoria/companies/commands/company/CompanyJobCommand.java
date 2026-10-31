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
import de.eldoria.companies.data.wrapper.job.JobKind;
import de.eldoria.companies.data.wrapper.job.JobReview;
import de.eldoria.companies.data.wrapper.job.JobState;
import de.eldoria.companies.jobs.Blueprint;
import de.eldoria.companies.jobs.BuildCheck;
import de.eldoria.companies.jobs.BuildGhosts;
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
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * {@code /company job}: the jobs companies take like orders, work in an area of a player's land or described in words.
 * <ul>
 *     <li>{@code /company job} lists the open jobs;</li>
 *     <li>{@code mine} lists the jobs the company works on;</li>
 *     <li>{@code info <id>} shows a job, and for one of the company on the server of its area how far the work
 *     is;</li>
 *     <li>{@code take <id>} takes an open job, with {@code manage_orders}, using one of the company's order slots;</li>
 *     <li>{@code giveback <id>} gives a job back; it is open again;</li>
 *     <li>{@code done <id>} checks the job's area on the server that holds it and, when the work is done, pays the
 *     members by the blocks each removed; for a contract it tells the owner the work is done, who approves or rejects
 *     it;</li>
 *     <li>{@code dispute <id> <reason>} opens a dispute an admin settles, {@code withdraw <id>} withdraws one the
 *     company opened;</li>
 *     <li>{@code ghosts <id>} shows the missing and wrong blocks of a build job, a layer at a time, with
 *     {@code ghosts next|previous <id>} to move and {@code ghosts off} to stop.</li>
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
                    var checked =
                            ours && job.state() == JobState.TAKEN && job.kind().area();
                    return new Shown(job, checked ? progress(job, false) : Optional.empty());
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
                    if (may == JobService.Change.HARVEST) {
                        jobs().access().flush(id);
                        var ended = jobs().service().endAsCompany(company, player.getUniqueId(), id);
                        return ended == JobService.Change.DONE
                                ? new JobService.Finished.Ended()
                                : new JobService.Finished.Refused(ended);
                    }
                    if (may == JobService.Change.CONTRACT) {
                        var marked = jobs().service().markDone(company, player.getUniqueId(), id);
                        if (marked != JobService.Change.DONE) return new JobService.Finished.Refused(marked);
                        var approveIn = deps.configuration().jobSettings().approveHours();
                        return new JobService.Finished.Waiting(Times.now().plusHours(approveIn));
                    }
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
                        case JobService.Finished.Ended ended -> {
                            jobs().access().checkSoon();
                            deps.messages().sendMessage(player, PREFIX + "ended", Values.number("id", id));
                        }
                        case JobService.Finished.Waiting waiting ->
                            deps.messages()
                                    .sendMessage(
                                            player,
                                            PREFIX + "marked",
                                            Values.number("id", id),
                                            Values.text("time", left(waiting.approveAt())));
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

    /**
     * Opens a dispute about a job of the player's company; an admin settles it.
     *
     * @param sender sender of the command
     * @param id     id of the job
     * @param reason why
     */
    @Executes("dispute")
    public void dispute(CommandSender sender, @IntArg(min = 0) int id, @StringArg(StringArgType.GREEDY) String reason) {
        var player = Senders.player(sender);
        run(
                player,
                () -> jobs().service().disputeAsCompany(company(player), player.getUniqueId(), id, reason),
                change -> {
                    if (change == JobService.Change.DONE) {
                        jobs().access().checkSoon();
                        deps.messages().sendMessage(player, PREFIX + "disputed", Values.number("id", id));
                        return;
                    }
                    refused(player, id, change);
                });
    }

    /**
     * Asks the owner of a job of the player's company for more time.
     *
     * @param sender sender of the command
     * @param id     id of the job
     * @param hours  the hours asked for
     * @param reason why
     */
    @Executes("extend")
    public void extend(
            CommandSender sender,
            @IntArg(min = 0) int id,
            @IntArg(min = 1, max = 8760) int hours,
            @StringArg(StringArgType.GREEDY) String reason) {
        var player = Senders.player(sender);
        run(
                player,
                () -> jobs().service().askExtension(company(player), player.getUniqueId(), id, hours, reason),
                change -> {
                    if (change == JobService.Change.DONE) {
                        deps.messages()
                                .sendMessage(
                                        player,
                                        PREFIX + "extensionAsked",
                                        Values.number("id", id),
                                        Values.number("hours", hours));
                        return;
                    }
                    refused(player, id, change);
                });
    }

    /**
     * Withdraws a dispute the player's company opened.
     *
     * @param sender sender of the command
     * @param id     id of the job
     */
    @Executes("withdraw")
    public void withdraw(CommandSender sender, @IntArg(min = 0) int id) {
        var player = Senders.player(sender);
        run(player, () -> jobs().service().withdrawAsCompany(company(player), player.getUniqueId(), id), change -> {
            if (change == JobService.Change.DONE) {
                jobs().access().checkSoon();
                deps.messages().sendMessage(player, PREFIX + "withdrawn", Values.number("id", id));
                return;
            }
            refused(player, id, change);
        });
    }

    /**
     * Shows the player the missing and wrong blocks of a build job of their company, starting with the lowest layer
     * that still lacks one.
     *
     * @param sender sender of the command
     * @param id     id of the job
     */
    @Executes("ghosts")
    public void ghosts(CommandSender sender, @IntArg(min = 0) int id) {
        ghosts(Senders.player(sender), id, Shift.FIRST);
    }

    /**
     * Shows the player the next layer up of a build job.
     *
     * @param sender sender of the command
     * @param id     id of the job
     */
    @Executes("ghosts next")
    public void ghostsNext(CommandSender sender, @IntArg(min = 0) int id) {
        ghosts(Senders.player(sender), id, Shift.UP);
    }

    /**
     * Shows the player the next layer down of a build job.
     *
     * @param sender sender of the command
     * @param id     id of the job
     */
    @Executes("ghosts previous")
    public void ghostsPrevious(CommandSender sender, @IntArg(min = 0) int id) {
        ghosts(Senders.player(sender), id, Shift.DOWN);
    }

    /**
     * Removes the ghost blocks the player sees.
     *
     * @param sender sender of the command
     */
    @Executes("ghosts off")
    public void ghostsOff(CommandSender sender) {
        var player = Senders.player(sender);
        jobs().ghosts().hide(player);
        deps.messages().sendMessage(player, PREFIX + "ghosts.off");
    }

    private void ghosts(Player player, int id, Shift shift) {
        run(
                player,
                () -> {
                    var company = company(player);
                    var job = jobs().data().job(id).orElseThrow(() -> unknown(id));
                    if (job.state() != JobState.TAKEN
                            || !Integer.valueOf(company.id()).equals(job.company())) {
                        throw CommandFailure.of(PREFIX + "error.notYours", Values.number("id", id));
                    }
                    if (job.kind() != JobKind.BUILD) {
                        throw CommandFailure.of(PREFIX + "error.noBlueprint", Values.number("id", id));
                    }
                    if (!job.node().equals(jobs().node())) {
                        throw CommandFailure.of(PREFIX + "error.otherServer", Values.number("id", id));
                    }
                    var blueprint = jobs().blueprints()
                            .of(id)
                            .orElseThrow(
                                    () -> CommandFailure.of(PREFIX + "error.noBlueprint", Values.number("id", id)));
                    return new Building(job, blueprint);
                },
                building -> Threading.mainThread(deps.plugin()).execute(() -> showGhosts(player, building, shift)));
    }

    private void showGhosts(Player player, Building building, Shift shift) {
        var job = building.job();
        var world = deps.plugin().getServer().getWorld(job.area().world());
        if (world == null || !player.isOnline()) return;
        var current = jobs().ghosts().layerOf(player, job.id());
        var y =
                switch (shift) {
                    case FIRST -> BuildGhosts.firstOpen(job.area(), building.blueprint(), BuildCheck.of(world));
                    case UP -> current.map(at -> at + 1).orElse(job.area().minY());
                    case DOWN -> current.map(at -> at - 1).orElse(job.area().minY());
                };
        jobs().ghosts().show(player, job, building.blueprint(), y).ifPresent(layer -> {
            var id = job.id();
            deps.messages()
                    .sendMessage(
                            player,
                            MenuText.create()
                                    .locale(
                                            PREFIX + "ghosts.layer",
                                            Values.number("id", id),
                                            Values.number("y", layer.y()),
                                            Values.number("open", layer.open()),
                                            Values.number("asked", layer.asked()))
                                    .text(
                                            " <act_down><show>[▼]</act_down> <act_up><show>[▲]</act_up>"
                                                    + " <act_off><remove>[<i18n:words.off>]</act_off>",
                                            Values.click(
                                                    "act_down",
                                                    ClickEvent.runCommand("/company job ghosts previous " + id)),
                                            Values.click(
                                                    "act_up", ClickEvent.runCommand("/company job ghosts next " + id)),
                                            Values.click("act_off", ClickEvent.runCommand("/company job ghosts off"))));
        });
    }

    /**
     * Which layer of a build job to show.
     */
    private enum Shift {
        FIRST,
        UP,
        DOWN
    }

    /**
     * A build job of the player's company and its blueprint.
     *
     * @param job       the job
     * @param blueprint its blueprint
     */
    private record Building(Job job, Blueprint blueprint) {}

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
        var time = job.state() == JobState.OPEN ? job.expires() : job.due();
        return MenuText.create()
                .locale(
                        PREFIX + (job.kind().area() ? "entry" : "entryContract"),
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
        var description = job.description();
        if (description != null) {
            messages.sendMessage(player, PREFIX + "info.description", Values.text("description", description));
        }
        if (!job.kind().area()) {
            if (!job.area().world().isEmpty()) {
                messages.sendMessage(
                        player,
                        PREFIX + "info.place",
                        Values.text("world", job.area().world()),
                        Values.text("center", job.area().center()));
            }
        } else {
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
        }
        messages.sendMessage(
                player,
                PREFIX + "info.money",
                Values.money("price", deps.services().economy(), job.price()),
                Values.number("hours", job.dueHours()));
        var harvest = job.harvest();
        if (harvest != null) {
            messages.sendMessage(
                    player,
                    PREFIX + "info.harvest",
                    Values.money("unit", deps.services().economy(), harvest.unitPrice()),
                    Values.money("paid", deps.services().economy(), harvest.paid()),
                    Values.money("price", deps.services().economy(), job.price()));
        }
        var target = job.target();
        if (target != null) {
            var surface = target.surface();
            if (surface == null) {
                messages.sendMessage(player, PREFIX + "info.target", Values.number("y", target.y()));
            } else {
                messages.sendMessage(
                        player,
                        PREFIX + "info.targetSurface",
                        Values.number("y", target.y()),
                        Values.text("block", surface.toLowerCase(Locale.ROOT)));
            }
        }
        if (job.state() == JobState.TAKEN) {
            messages.sendMessage(player, PREFIX + "info.due", Values.text("time", left(job.due())));
        }
        var extension = job.extension();
        if (job.state() == JobState.TAKEN && extension.extendedHours() > 0) {
            messages.sendMessage(player, PREFIX + "info.extended", Values.number("hours", extension.extendedHours()));
        }
        var asked = extension.askedHours();
        if (job.state() == JobState.TAKEN && asked != null) {
            messages.sendMessage(player, PREFIX + "info.extensionAsked", Values.number("hours", asked));
        }
        var review = job.review();
        var rejected = review.rejected();
        if (job.state() == JobState.TAKEN && rejected != null) {
            messages.sendMessage(player, PREFIX + "info.rejected", Values.text("reason", rejected));
        }
        if (job.state() == JobState.DONE) {
            messages.sendMessage(player, PREFIX + "info.waiting");
        }
        if (job.state() == JobState.DISPUTED) {
            messages.sendMessage(
                    player,
                    PREFIX + "info.disputed",
                    Values.text("reason", Objects.requireNonNullElse(review.dispute(), "")));
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
            var done =
                    switch (job.kind()) {
                        case CONTRACT -> "markButton";
                        case HARVEST -> "endButton";
                        default -> "doneButton";
                    };
            buttons.text(
                            "<act_done><add>[<i18n:" + PREFIX + done + ">]</act_done> ",
                            Values.click("act_done", ClickEvent.runCommand("/company job done " + id)))
                    .text(
                            "<act_back><remove>[<i18n:" + PREFIX + "giveBackButton>]</act_back> ",
                            Values.click("act_back", ClickEvent.suggestCommand("/company job giveback " + id)));
            var limits = deps.configuration().jobSettings();
            if (limits.extensionsEnabled()
                    && !job.extension().asked()
                    && job.extension().requests() < limits.maxExtensionRequests()
                    && jobs().service().hoursLeft(job) > 0) {
                buttons.text(
                        "<act_extend><show>[<i18n:" + PREFIX + "extendButton>]</act_extend> ",
                        Values.click("act_extend", ClickEvent.suggestCommand("/company job extend " + id + " ")));
            }
        }
        if ((job.state() == JobState.TAKEN || job.state() == JobState.DONE) && job.kind() != JobKind.HARVEST) {
            buttons.text(
                    "<act_dispute><remove>[<i18n:" + PREFIX + "disputeButton>]</act_dispute>",
                    Values.click("act_dispute", ClickEvent.suggestCommand("/company job dispute " + id + " ")));
        }
        var disputeBy = job.review().disputeBy();
        if (job.state() == JobState.DISPUTED && disputeBy != null && !disputeBy.equals(job.owner())) {
            buttons.text(
                    "<act_withdraw><show>[<i18n:" + PREFIX + "withdrawButton>]</act_withdraw>",
                    Values.click("act_withdraw", ClickEvent.suggestCommand("/company job withdraw " + id)));
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
                    case DISPUTED -> "error.disputed";
                    case REASON -> "error.reason";
                    case HARVEST -> "error.harvest";
                    case DISABLED -> "error.extensionsOff";
                    case HOURS -> "error.extensionHours";
                    case ASKED -> "error.extensionAsked";
                    case REQUESTS -> "error.extensionRequests";
                    case DONE, TAKEN, TOO_LONG, AREA, CONTRACT, NOT_DONE, NO_COMPANY, NO_REQUEST -> "error.closed";
                };
        var job = change == JobService.Change.HOURS || change == JobService.Change.REQUESTS
                ? jobs().data().job(id).orElse(null)
                : null;
        var left = job == null ? 0 : jobs().service().hoursLeft(job);
        var asked = job == null ? 0 : job.extension().requests();
        deps.messages()
                .sendError(
                        player,
                        PREFIX + key,
                        values,
                        Values.number("left", left),
                        Values.number("max", asked),
                        Values.number("length", JobReview.MAX_REASON));
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
