/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Permission.Admin;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;

/**
 * {@code /companyadmin reload}: reloads the configuration and applies the update settings and everything registered
 * with {@link CommandDependencies#onReload(Runnable)}. Works from the console too.
 */
@Subcommand("reload")
@Permission(Admin.RELOAD)
public final class Reload {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public Reload(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * @param sender sender of the command
     */
    @Executes
    public void reload(CommandSender sender) {
        var updates = deps.services().updates();
        deps.configuration().reload();
        updates.apply();
        deps.reloaded();
        deps.adminLog().entry(sender, "reload").write();
        var unreadable = deps.configuration().unreadableFiles();
        if (unreadable.isEmpty()) {
            deps.messages().sendMessage(sender, "companyadmin.reload.done");
            return;
        }
        deps.messages()
                .sendError(
                        sender, "companyadmin.reload.unreadable", Values.text("files", String.join(", ", unreadable)));
    }
}
