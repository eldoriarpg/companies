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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;

/**
 * Ends the dashboard sessions of a player whose admin sandbox starts or ends, its expiry included, and of a player who
 * leaves the server while in it. A dashboard session keeps the permissions it was opened with, so without this the
 * admin tab would outlive the sandbox; the player logs in again with what they may do now. LuckPerms tells of an
 * expiry only while the player is loaded, so leaving is covered by itself, and {@link #revokeLater(String, Duration)}
 * covers the end of a sandbox taken on this server.
 */
final class SandboxSessions implements Listener {
    private final Plugin plugin;

    /**
     * @param plugin runs the revoke on the main thread
     */
    SandboxSessions(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @param luckPerms the permissions whose changes are followed
     */
    void listen(LuckPerms luckPerms) {
        var events = luckPerms.getEventBus();
        events.subscribe(plugin, NodeAddEvent.class, event -> changed(event.getTarget(), event.getNode()));
        events.subscribe(plugin, NodeRemoveEvent.class, event -> changed(event.getTarget(), event.getNode()));
    }

    private void changed(PermissionHolder target, Node node) {
        if (!(target instanceof User user) || !isSandbox(node)) return;
        var name = user.getUsername();
        if (name == null) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> revoke(name));
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
     * Ends the dashboard sessions of a player once their sandbox ran out, wherever they are then.
     *
     * @param name  the player
     * @param after how long the sandbox lasts
     */
    void revokeLater(String name, Duration after) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> revoke(name), after.toSeconds() * 20 + 20);
    }

    private void revoke(String name) {
        plugin.getServer()
                .dispatchCommand(plugin.getServer().getConsoleSender(), "companyadmin dashboard revoke " + name);
    }

    /**
     * @param node a node of a player
     * @return whether it is the membership of the sandbox group
     */
    static boolean isSandbox(Node node) {
        return node instanceof InheritanceNode group && group.getGroupName().equals(AdminSandbox.GROUP);
    }
}
