/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import org.bukkit.plugin.Plugin;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Threading {
    public static final ExecutorService VIRTUAL = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Creates an executor which runs tasks on the server main thread.
     * <p>
     * Inventories and the economy must only be touched from the main thread.
     *
     * @param plugin plugin owning the scheduled tasks
     * @return executor for the main thread
     */
    public static Executor mainThread(Plugin plugin) {
        return task -> plugin.getServer().getScheduler().runTask(plugin, task);
    }
}
