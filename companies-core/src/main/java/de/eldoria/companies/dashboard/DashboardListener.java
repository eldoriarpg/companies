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

import java.util.function.Supplier;

/**
 * Shows admins the dashboard's deprecation notices when they join, refreshes the remembered permissions of players
 * with dashboard sessions, and hands the joins and quits to the {@link PresenceReporter}.
 */
public final class DashboardListener implements Listener {
    private final Dashboard dashboard;
    private final Supplier<MessageSender> messages;
    private final PresenceReporter presence;

    /**
     * @param dashboard the dashboard
     * @param messages  the plugin's message sender
     * @param presence  reports joins and quits to the gateway
     */
    public DashboardListener(Dashboard dashboard, Supplier<MessageSender> messages, PresenceReporter presence) {
        this.dashboard = dashboard;
        this.messages = messages;
        this.presence = presence;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        presence.joined(player.getUniqueId());
        refresh(player);
        if (!player.hasPermission(Permission.Admin.ADMIN)) return;
        for (var notice : dashboard.notices()) {
            messages.get().sendError(player, "dashboard.notice", Values.text("notice", notice));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        presence.quit(event.getPlayer().getUniqueId());
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
