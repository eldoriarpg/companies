/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.node.NodeAddEvent;
import net.luckperms.api.event.node.NodeRemoveEvent;
import net.luckperms.api.model.PermissionHolder;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Ends the dashboard sessions of a player whose admin sandbox starts or ends, its expiry included, and of a player who
 * leaves the server while in it. A dashboard session keeps the permissions it was opened with, so without this the
 * admin tab would outlive the sandbox; the player logs in again with what they may do now. LuckPerms tells of an
 * expiry only while the player is loaded, so leaving is covered by itself, and a revoke planned for the end of the
 * sandbox covers the rest.
 * <p>
 * The plan lives in memory, so the {@link SandboxLedger} keeps who took the sandbox here. When the server starts every
 * player kept there and every player online is checked, and every player when they join: a sandbox that still runs is
 * planned again for its end, one that ended meanwhile has its sessions ended at once. A player never kept is left
 * alone. A removed sandbox node and a planned revoke are checked the same way against the nodes the player has now,
 * so an old node or plan never ends a sandbox taken again since.
 */
final class SandboxSessions implements Listener {
    private final Plugin plugin;
    private final LuckPerms luckPerms;
    private final SandboxLedger ledger;
    private final Clock clock;
    private final Map<UUID, BukkitTask> planned = new HashMap<>();

    /**
     * @param plugin    runs the revoke on the main thread
     * @param luckPerms the permissions whose changes are followed
     * @param ledger    the players who took the sandbox here
     * @param clock     the time the end of a sandbox is planned from
     */
    SandboxSessions(Plugin plugin, LuckPerms luckPerms, SandboxLedger ledger, Clock clock) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
        this.ledger = ledger;
        this.clock = clock;
    }

    /**
     * Follows the sandbox nodes from now on and checks the players kept and online.
     */
    void listen() {
        var events = luckPerms.getEventBus();
        events.subscribe(plugin, NodeAddEvent.class, event -> added(event.getTarget(), event.getNode()));
        events.subscribe(plugin, NodeRemoveEvent.class, event -> removed(event.getTarget(), event.getNode()));
        var players = new HashMap<>(ledger.all());
        plugin.getServer().getOnlinePlayers().forEach(player -> players.put(player.getUniqueId(), player.getName()));
        players.forEach((player, name) -> check(player, name, false));
    }

    private void added(PermissionHolder target, Node node) {
        if (!(target instanceof User user) || !isSandbox(node)) return;
        var name = user.getUsername();
        if (name == null) return;
        onMain(() -> revoke(name));
        check(user.getUniqueId(), name, false);
    }

    private void removed(PermissionHolder target, Node node) {
        if (!(target instanceof User user) || !isSandbox(node)) return;
        var name = user.getUsername();
        if (name == null) return;
        check(user.getUniqueId(), name, true);
    }

    /**
     * Checks the sandbox of a player who joins.
     *
     * @param event the player joining
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        check(event.getPlayer().getUniqueId(), event.getPlayer().getName(), false);
    }

    /**
     * Ends the dashboard sessions of a player who leaves while in the sandbox.
     *
     * @param event the player leaving
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().hasPermission("group." + AdminSandbox.GROUP))
            revoke(event.getPlayer().getName());
    }

    /**
     * Keeps a player who took the sandbox and plans to end their dashboard sessions once it ran out, wherever they are
     * then. May be called from any thread.
     *
     * @param player id of the player
     * @param name   their name
     * @param after  how long the sandbox lasts
     */
    void started(UUID player, String name, Duration after) {
        onMain(() -> {
            ledger.add(player, name);
            plan(player, name, after);
        });
    }

    private void check(UUID player, String name, boolean ended) {
        luckPerms
                .getUserManager()
                .loadUser(player)
                .thenAccept(user -> {
                    var current = user.getUsername() == null ? name : user.getUsername();
                    var ends = ends(user.getNodes());
                    onMain(() -> apply(player, current, decide(ledger.contains(player), ended, ends, clock.instant())));
                })
                .exceptionally(err -> {
                    plugin.getLogger().log(Level.WARNING, "Could not check the admin sandbox of " + name, err);
                    return null;
                });
    }

    private void apply(UUID player, String name, Action action) {
        switch (action) {
            case Action.Revoke revoke -> {
                forget(player);
                if (!name.isBlank()) revoke(name);
            }
            case Action.Keep keep -> {
                ledger.add(player, name);
                cancelPlan(player);
            }
            case Action.Plan(var after) -> {
                ledger.add(player, name);
                plan(player, name, after);
            }
            case Action.Ignore ignore -> {}
        }
    }

    /**
     * What to do about the sandbox of a player once their nodes are known.
     *
     * @param kept  whether the ledger keeps the player
     * @param ended whether a sandbox of the player was just seen ending
     * @param ends  when their sandbox ends, as {@link #ends(Collection)} tells
     * @param now   the time now
     * @return what to do
     */
    static Action decide(boolean kept, boolean ended, Optional<Instant> ends, Instant now) {
        if (ends.isEmpty()) return kept || ended ? new Action.Revoke() : new Action.Ignore();
        if (ends.get().equals(Instant.MAX)) return new Action.Keep();
        return new Action.Plan(Duration.between(now, ends.get()));
    }

    private void plan(UUID player, String name, Duration after) {
        var ticks = Math.max(0, after.toSeconds()) * 20 + 20;
        var task = plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            planned.remove(player);
                            check(player, name, true);
                        },
                        ticks);
        var former = planned.put(player, task);
        if (former != null) former.cancel();
    }

    private void forget(UUID player) {
        ledger.remove(player);
        cancelPlan(player);
    }

    private void cancelPlan(UUID player) {
        var task = planned.remove(player);
        if (task != null) task.cancel();
    }

    private void onMain(Runnable task) {
        if (plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin, task);
    }

    private void revoke(String name) {
        plugin.getServer()
                .dispatchCommand(plugin.getServer().getConsoleSender(), "companyadmin dashboard revoke " + name);
    }

    /**
     * What to do about the sandbox of a player.
     */
    sealed interface Action {
        /**
         * The sandbox ended: forget the player and end their dashboard sessions.
         */
        record Revoke() implements Action {}

        /**
         * The sandbox has no end: keep the player, plan nothing.
         */
        record Keep() implements Action {}

        /**
         * The sandbox runs: keep the player and plan the revoke for its end.
         *
         * @param after how long until it ends
         */
        record Plan(Duration after) implements Action {}

        /**
         * The player has no sandbox and was never kept here.
         */
        record Ignore() implements Action {}
    }

    /**
     * @param nodes the nodes of a player
     * @return when the sandbox of the player ends, {@link Instant#MAX} for one without an end, empty when they have
     *         none
     */
    static Optional<Instant> ends(Collection<? extends Node> nodes) {
        return nodes.stream()
                .filter(node -> isSandbox(node) && !node.hasExpired())
                .map(node -> node.getExpiry() == null ? Instant.MAX : node.getExpiry())
                .max(Instant::compareTo);
    }

    /**
     * @param node a node of a player
     * @return whether it is the membership of the sandbox group
     */
    static boolean isSandbox(Node node) {
        return node instanceof InheritanceNode group && group.getGroupName().equals(AdminSandbox.GROUP);
    }
}
