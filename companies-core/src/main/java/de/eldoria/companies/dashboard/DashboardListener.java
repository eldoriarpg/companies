/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard;

import de.eldoria.companies.dashboard.sessions.SessionPermissions;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Permission;
import de.eldoria.eldoutilities.messages.MessageSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Shows admins the dashboard's deprecation notices when they join, refreshes the remembered permissions of players
 * with dashboard sessions, and keeps the players online on this node, reporting their joins and quits to the gateway.
 */
public final class DashboardListener implements Listener {
    private final Dashboard dashboard;
    private final Supplier<MessageSender> messages;
    private final Set<UUID> online;

    /**
     * @param dashboard the dashboard
     * @param messages  the plugin's message sender
     * @param online    the players online on this node, kept up to date here
     */
    public DashboardListener(Dashboard dashboard, Supplier<MessageSender> messages, Set<UUID> online) {
        this.dashboard = dashboard;
        this.messages = messages;
        this.online = online;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        online.add(player.getUniqueId());
        dashboard.presence(player.getUniqueId(), true);
        refresh(player);
        if (!player.hasPermission(Permission.Admin.ADMIN)) return;
        for (var notice : dashboard.notices()) {
            messages.get().sendError(player, "dashboard.notice", Values.text("notice", notice));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer().getUniqueId();
        online.remove(player);
        dashboard.presence(player, false);
    }

    /**
     * Remembers the permissions of an online player with the player's sessions.
     *
     * @param player the player, on the main thread
     */
    public void refresh(Player player) {
        dashboard.permissionsSeen(
                player.getUniqueId(), SessionPermissions.of(player), player.hasPermission(Permission.Dashboard.USE));
    }
}
