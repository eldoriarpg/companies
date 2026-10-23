/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

/**
 * Fires events that are declared asynchronous. Bukkit refuses an asynchronous event on the main thread, so such an
 * event is handed to a virtual thread when the caller is on the main thread and fired directly otherwise.
 */
public final class AsyncEvents {
    private AsyncEvents() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Fires an asynchronous event off the main thread.
     *
     * @param plugin plugin whose plugin manager fires the event
     * @param event  event declared asynchronous
     */
    public static void fire(Plugin plugin, Event event) {
        if (Bukkit.isPrimaryThread()) {
            Threading.VIRTUAL.execute(
                    () -> plugin.getServer().getPluginManager().callEvent(event));
            return;
        }
        plugin.getServer().getPluginManager().callEvent(event);
    }
}
