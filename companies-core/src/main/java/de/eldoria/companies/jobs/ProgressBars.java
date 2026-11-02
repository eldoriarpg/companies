/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.components.job.JobState;
import de.eldoria.companies.data.wrapper.job.Job;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Shows a member of the company working on a dig, terraform or build job of this server how far the work is, as a boss
 * bar while they stand in its area, and hides it when they leave. The progress comes from {@link JobProgress}, so an
 * area is checked at most every {@value JobProgress#CACHE_SECONDS} seconds however many members stand in it. Refreshed
 * every {@value #REFRESH_TICKS} ticks on the main thread.
 */
public final class ProgressBars implements Listener {
    /**
     * How often the bars are refreshed, in ticks.
     */
    public static final long REFRESH_TICKS = 40;

    private final Plugin plugin;
    private final Supplier<List<JobAccess.Active>> active;
    private final Function<Job, CompletableFuture<Optional<DigCheck.Progress>>> progress;
    private final Executor mainThread;
    private final Label label;
    private final Map<UUID, Shown> shown = new ConcurrentHashMap<>();

    /**
     * Names a bar.
     */
    @FunctionalInterface
    public interface Label {
        /**
         * @param player   the player seeing the bar
         * @param job      the job
         * @param progress how far its work is
         * @return the text of the bar
         */
        Component of(Player player, Job job, DigCheck.Progress progress);
    }

    /**
     * @param plugin     the plugin
     * @param active     the jobs taken on this server and who works on them, as {@link JobAccess#active()}
     * @param progress   reads how far the work on a job is, as {@link JobProgress#of(Job, boolean)} without a fresh
     *                   check
     * @param mainThread runs on the main thread
     * @param label      names a bar
     */
    public ProgressBars(
            Plugin plugin,
            Supplier<List<JobAccess.Active>> active,
            Function<Job, CompletableFuture<Optional<DigCheck.Progress>>> progress,
            Executor mainThread,
            Label label) {
        this.plugin = plugin;
        this.active = active;
        this.progress = progress;
        this.mainThread = mainThread;
        this.label = label;
    }

    /**
     * Shows each online player standing in the area of a job their company works on how far it is, and hides the bar
     * of a player who left it.
     */
    public void refresh() {
        for (var player : plugin.getServer().getOnlinePlayers()) {
            var job = jobAt(player);
            if (job == null) {
                hide(player);
                continue;
            }
            progress.apply(job)
                    .thenAcceptAsync(
                            found -> {
                                var still = jobAt(player);
                                if (!player.isOnline() || still == null || still.id() != job.id()) return;
                                found.ifPresentOrElse(work -> show(player, job, work), () -> hide(player));
                            },
                            mainThread)
                    .exceptionally(err -> {
                        plugin.getLogger().log(Level.FINE, "Could not read the progress of job " + job.id(), err);
                        return null;
                    });
        }
    }

    /**
     * @param player a player
     * @return the job whose area the player stands in and whose company they belong to, null for none
     */
    @Nullable
    Job jobAt(Player player) {
        var at = player.getLocation();
        var world = at.getWorld();
        if (world == null) return null;
        for (var entry : active.get()) {
            var job = entry.job();
            if (job.state() == JobState.TAKEN
                    && job.kind().area()
                    && job.kind() != JobKind.HARVEST
                    && entry.members().contains(player.getUniqueId())
                    && job.area().contains(world.getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ())) {
                return job;
            }
        }
        return null;
    }

    private void show(Player player, Job job, DigCheck.Progress work) {
        var name = label.of(player, job, work);
        var share = Math.clamp(work.percent() / 100f, 0f, 1f);
        var current = shown.get(player.getUniqueId());
        if (current != null && current.job() == job.id()) {
            current.bar().name(name).progress(share);
            return;
        }
        hide(player);
        var bar = BossBar.bossBar(name, share, BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_10);
        shown.put(player.getUniqueId(), new Shown(job.id(), bar));
        player.showBossBar(bar);
    }

    /**
     * @param player a player
     * @return the id of the job whose bar the player sees, -1 for none
     */
    int shownTo(Player player) {
        var current = shown.get(player.getUniqueId());
        return current == null ? -1 : current.job();
    }

    private void hide(Player player) {
        var current = shown.remove(player.getUniqueId());
        if (current != null) player.hideBossBar(current.bar());
    }

    /**
     * Hides every bar.
     */
    public void stop() {
        for (var entry : shown.entrySet()) {
            var player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) player.hideBossBar(entry.getValue().bar());
        }
        shown.clear();
    }

    /**
     * @param event the player leaving
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        shown.remove(event.getPlayer().getUniqueId());
    }

    private record Shown(int job, BossBar bar) {}
}
