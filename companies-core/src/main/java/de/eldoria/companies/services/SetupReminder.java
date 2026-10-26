/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.configuration.SetupPages;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.admin.SetupMenu;
import de.eldoria.companies.util.Permission;
import de.eldoria.eldoutilities.messages.MessageSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Reminds admins that the setup is not finished: once in the console when the plugin starts, and once per join in the
 * chat of a player with the admin permission, with a click that opens {@code /companyadmin setup}. It stays quiet once
 * every setup page is done or skipped, or an admin hid the reminder on the dashboard.
 */
public final class SetupReminder implements Listener {
    private static final long DELAY_TICKS = 60L;
    private static final String BANNER = "banner";

    private final Plugin plugin;
    private final Supplier<ANodeData> nodeData;
    private final MessageSender messages;
    private final Executor work;

    /**
     * @param plugin   the plugin, for the scheduler and the log
     * @param nodeData the node repository holding the setup's progress
     * @param messages sends the reminder
     * @param work     reads the progress off the main thread
     */
    public SetupReminder(Plugin plugin, Supplier<ANodeData> nodeData, MessageSender messages, Executor work) {
        this.plugin = plugin;
        this.nodeData = nodeData;
        this.messages = messages;
        this.work = work;
    }

    /**
     * Logs one line when the setup is not finished, for the admin starting the server.
     */
    public void atStart() {
        work.execute(() -> {
            if (open()) {
                plugin.getLogger()
                        .info("The setup of Companies is not finished. Join and run " + SetupMenu.COMMAND
                                + ", continue on the dashboard, or hide this with " + SetupMenu.COMMAND + " dismiss.");
            }
        });
    }

    /**
     * Reminds an admin who joins.
     *
     * @param event the join
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        if (!player.hasPermission(Permission.Admin.ADMIN)) return;
        work.execute(() -> {
            if (!open()) return;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> remind(player), DELAY_TICKS);
        });
    }

    private void remind(Player player) {
        if (!player.isOnline()) return;
        messages.sendMessage(
                player,
                MenuText.create()
                        .locale("companyadmin.setup.reminder")
                        .text(" <click:run_command:'" + SetupMenu.COMMAND
                                + "'><add>[<i18n:companyadmin.setup.reminderOpen>]</click> <click:run_command:'"
                                + SetupMenu.COMMAND
                                + " dismiss'><remove>[<i18n:companyadmin.setup.reminderHide>]</click>"));
    }

    /**
     * @return whether a setup page is still open and the reminder was not hidden
     */
    boolean open() {
        try {
            var progress = nodeData.get().setupProgress();
            return open(progress);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.FINE, "Could not read the setup's progress.", e);
            return false;
        }
    }

    /**
     * @param progress the state of each page by its id
     * @return whether a setup page is still open and the reminder was not hidden
     */
    static boolean open(Map<String, String> progress) {
        if ("dismissed".equals(progress.get(BANNER))) return false;
        return SetupPages.PAGES.stream().anyMatch(page -> "open".equals(progress.getOrDefault(page.id(), "open")));
    }
}
