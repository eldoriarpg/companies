/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.configuration.elements.jobs.JobSettings;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobKind;
import de.eldoria.companies.data.wrapper.job.JobState;
import de.eldoria.companies.jobs.DigCheck;
import de.eldoria.companies.jobs.JobService;
import de.eldoria.companies.jobs.JobServices;
import de.eldoria.companies.jobs.hooks.JobHooks;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.util.Texts;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.DoubleArg;
import net.strokkur.commands.arguments.IntArg;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * {@code /order job}: the player's jobs, work in an area of their land that a company takes like an order.
 * <ul>
 *     <li>{@code /order job} lists the player's jobs;</li>
 *     <li>{@code info <id>} shows a job, and on the server of its area how far the work is;</li>
 *     <li>{@code dig <price> <hours> <title>} posts a dig job on the area selected with WorldEdit;</li>
 *     <li>{@code dig plot <price> <hours> <depth> <title>} posts one on the whole plot the player stands on, from the
 *     block below their feet down {@code depth} blocks;</li>
 *     <li>{@code cancel <id>} cancels a job nobody works on; the price goes back, the fee is kept.</li>
 * </ul>
 * The owner pays the price and the order fee when posting. The area must lie on the owner's plot or region, or on
 * unowned land in a world where the server allows it.
 */
@Subcommand("job")
@PlayerOnly
@net.strokkur.commands.permission.Permission(Permission.Orders.JOBS)
public final class JobCommand {
    static final String PREFIX = "order.job.";
    private static final int LISTED = 20;
    private static final int MAX_DEPTH = 384;

    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public JobCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Lists the player's jobs.
     *
     * @param sender sender of the command
     */
    @Executes
    public void list(CommandSender sender) {
        var player = Senders.player(sender);
        run(player, () -> jobs().data().ofOwner(player.getUniqueId(), LISTED), found -> {
            if (found.isEmpty()) {
                deps.messages().sendMessage(player, PREFIX + "none");
                return;
            }
            deps.messages()
                    .sendMessage(player, MenuText.create().text("<heading>").locale(PREFIX + "title"));
            for (var job : found) deps.messages().sendMessage(player, entry(player, job));
        });
    }

    /**
     * Shows a job of the player.
     *
     * @param sender sender of the command
     * @param id     id of the job
     */
    @Executes("info")
    public void info(CommandSender sender, @IntArg(min = 0) int id) {
        var player = Senders.player(sender);
        run(player, () -> own(player, id), job -> show(player, job, progress(job)));
    }

    /**
     * Posts a dig job on the area the player selected with WorldEdit.
     *
     * @param sender sender of the command
     * @param price  what the company is paid
     * @param hours  how long a company has to finish
     * @param title  title shown to companies
     */
    @Executes("dig")
    public void dig(
            CommandSender sender,
            @DoubleArg(min = 0, max = JobService.MAX_PRICE) double price,
            @IntArg(min = 1) int hours,
            @StringArg(StringArgType.GREEDY) String title) {
        var player = Senders.player(sender);
        var hooks = jobs().hooks();
        if (!hooks.selects()) {
            deps.messages().sendError(player, PREFIX + "error.noWorldEdit");
            return;
        }
        var area = hooks.selection(player);
        if (area.isEmpty()) {
            deps.messages().sendError(player, PREFIX + "error.noSelection");
            return;
        }
        post(player, area.get(), price, hours, title);
    }

    /**
     * Posts a dig job on the whole plot the player stands on, from the block below their feet down.
     *
     * @param sender sender of the command
     * @param price  what the company is paid
     * @param hours  how long a company has to finish
     * @param depth  how many layers to dig, starting with the one below the player's feet
     * @param title  title shown to companies
     */
    @Executes("dig plot")
    public void digPlot(
            CommandSender sender,
            @DoubleArg(min = 0, max = JobService.MAX_PRICE) double price,
            @IntArg(min = 1) int hours,
            @IntArg(min = 1, max = MAX_DEPTH) int depth,
            @StringArg(StringArgType.GREEDY) String title) {
        var player = Senders.player(sender);
        var plot = jobs().hooks().ownArea(player);
        if (plot.isEmpty()) {
            deps.messages().sendError(player, PREFIX + "error.noPlot");
            return;
        }
        var top = player.getLocation().getBlockY() - 1;
        var found = plot.get();
        var area = new JobArea(
                found.world(), found.minX(), top - depth + 1, found.minZ(), found.maxX(), top, found.maxZ());
        post(player, area, price, hours, title);
    }

    /**
     * Cancels a job of the player that nobody works on.
     *
     * @param sender sender of the command
     * @param id     id of the job
     */
    @Executes("cancel")
    public void cancel(CommandSender sender, @IntArg(min = 0) int id) {
        var player = Senders.player(sender);
        run(player, () -> jobs().service().cancel(player.getUniqueId(), player.getName(), id), change -> {
            var values = Values.number("id", id);
            switch (change) {
                case DONE -> {
                    deps.messages().sendMessage(player, PREFIX + "cancelled", values);
                    jobs().access().checkSoon();
                }
                case UNKNOWN, NOT_YOURS -> deps.messages().sendError(player, PREFIX + "error.unknown", values);
                case TAKEN -> deps.messages().sendError(player, PREFIX + "error.taken", values);
                default -> deps.messages().sendError(player, PREFIX + "error.closed", values);
            }
        });
    }

    private void post(Player player, JobArea area, double price, int hours, String title) {
        var world = deps.plugin().getServer().getWorld(area.world());
        if (world == null || area.minY() < world.getMinHeight() || area.maxY() >= world.getMaxHeight()) {
            deps.messages()
                    .sendError(
                            player,
                            PREFIX + "error.height",
                            Values.number("min", world == null ? 0 : world.getMinHeight()),
                            Values.number("max", world == null ? 0 : world.getMaxHeight() - 1));
            return;
        }
        var settings = deps.configuration().jobSettings();
        var decision = jobs().hooks().decide(player, area, settings.unownedAllowed(area.world()));
        switch (decision) {
            case JobHooks.Decision.Refused refused -> {
                deps.messages()
                        .sendError(
                                player,
                                PREFIX + "error.foreign",
                                Values.text("land", Objects.requireNonNullElse(refused.ref(), "?")));
                return;
            }
            case JobHooks.Decision.Unowned unowned -> {
                deps.messages().sendError(player, PREFIX + "error.unowned");
                return;
            }
            case JobHooks.Decision.Land land -> {
                var owner = player.getUniqueId();
                var node = jobs().node();
                run(
                        player,
                        () -> jobs().service()
                                .post(owner, JobKind.DIG, title, price, hours, node, area, land.land(), land.ref()),
                        posted -> posted(player, posted));
            }
        }
    }

    private void posted(Player player, JobService.Posted posted) {
        switch (posted) {
            case JobService.Posted.Done done -> {
                var job = done.job();
                deps.messages()
                        .sendMessage(
                                player,
                                PREFIX + "posted",
                                Values.number("id", job.id()),
                                Values.text("job_title", job.title()),
                                Values.money("price", deps.services().economy(), job.price()),
                                Values.money("fee", deps.services().economy(), job.fee()),
                                Values.number("blocks", job.area().volume()));
            }
            case JobService.Posted.Refused refused -> {
                var settings = deps.configuration().jobSettings();
                deps.messages()
                        .sendError(
                                player,
                                PREFIX + "error." + key(refused.refusal()),
                                Values.number("max", maxOf(refused.refusal(), settings)));
            }
        }
    }

    private static Number maxOf(JobService.Refusal refusal, JobSettings settings) {
        return switch (refusal) {
            case VOLUME -> settings.digMaxVolume();
            case CHUNKS -> settings.maxChunks();
            case TOO_MANY -> settings.maxOpenPerOwner();
            case DUE -> settings.maxDueHours();
            case TITLE -> Job.MAX_TITLE;
            default -> 0;
        };
    }

    private Job own(Player player, int id) {
        return jobs().data()
                .job(id)
                .filter(job -> job.owner().equals(player.getUniqueId()))
                .orElseThrow(() -> CommandFailure.of(PREFIX + "error.unknown", Values.number("id", id)));
    }

    /**
     * Reads how far a job is on the server of its area, kept for a few seconds.
     *
     * @param job the job
     * @return the progress, empty for a closed job, one of another server or a world not loaded
     */
    private Optional<DigCheck.Progress> progress(Job job) {
        if (job.state().closed()) return Optional.empty();
        return jobs().progress().of(job, false).join();
    }

    private void show(Player player, Job job, Optional<DigCheck.Progress> progress) {
        var economy = deps.services().economy();
        var messages = deps.messages();
        messages.sendMessage(
                player,
                PREFIX + "info.head",
                Values.number("id", job.id()),
                Values.text("job_title", job.title()),
                Values.component("state", texts(player).locale(stateKey(job.state()))));
        messages.sendMessage(
                player,
                PREFIX + "info.area",
                Values.text("world", job.area().world()),
                Values.text("center", job.area().center()),
                Values.number("blocks", job.area().volume()),
                Values.component("land", land(player, job)));
        messages.sendMessage(
                player,
                PREFIX + "info.money",
                Values.money("price", economy, job.price()),
                Values.money("fee", economy, job.fee()));
        if (job.state() == JobState.OPEN) {
            messages.sendMessage(player, PREFIX + "info.open", Values.text("time", left(job.expires())));
        }
        var due = job.due();
        if (job.state() == JobState.TAKEN && due != null) {
            messages.sendMessage(player, PREFIX + "info.taken", Values.text("time", left(due)));
        }
        progress.ifPresent(found -> messages.sendMessage(
                player,
                PREFIX + "info.progress",
                Values.number("percent", found.percent()),
                Values.number("cleared", found.cleared()),
                Values.number("total", found.total())));
    }

    private MenuText entry(Player player, Job job) {
        var economy = deps.services().economy();
        var id = job.id();
        return MenuText.create()
                .locale(
                        PREFIX + "entry",
                        Values.number("id", id),
                        Values.text("job_title", job.title()),
                        Values.component("state", texts(player).locale(stateKey(job.state()))),
                        Values.money("price", economy, job.price()))
                .text(
                        " <act_info><show>[<i18n:words.info>]</act_info>",
                        Values.click("act_info", ClickEvent.runCommand("/order job info " + id)));
    }

    /**
     * @param state the state of a job
     * @return the locale key naming it
     */
    static String stateKey(JobState state) {
        return "jobs.state." + state.name().toLowerCase(Locale.ROOT);
    }

    private Component land(Player player, Job job) {
        var text = MenuText.create().locale("jobs.land." + job.land().name().toLowerCase(Locale.ROOT));
        var ref = job.landRef();
        if (ref != null) text.text(" <ref>", Values.text("ref", ref));
        return texts(player).of(text);
    }

    private MenuComponents texts(Player player) {
        return new MenuComponents(deps.messages(), player);
    }

    /**
     * @param until a time
     * @return the time from now until then, never below 0
     */
    static String left(@Nullable LocalDateTime until) {
        if (until == null) return Texts.prettyDuration(Duration.ZERO);
        var duration = Duration.between(Times.now(), until);
        return Texts.prettyDuration(duration.isNegative() ? Duration.ZERO : duration);
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
     * @param refusal why a job was refused
     * @return the refusal's message key below {@code error.}, in camel case
     */
    private static String key(JobService.Refusal refusal) {
        var parts = refusal.name().toLowerCase(Locale.ROOT).split("_");
        var key = new StringBuilder(parts[0]);
        for (var i = 1; i < parts.length; i++) {
            key.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return key.toString();
    }
}
