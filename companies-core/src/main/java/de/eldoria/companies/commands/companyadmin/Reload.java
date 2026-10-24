/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.util.Permission.Admin;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;

/**
 * {@code /companyadmin reload}: reloads the configuration and applies the update settings and everything registered
 * with {@link CommandDependencies#onReload(Runnable)}.
 */
@Subcommand("reload")
@Permission(Admin.RELOAD)
@PlayerOnly
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
        var player = Senders.player(sender);
        var updates = deps.services().updates();
        deps.configuration().reload();
        updates.apply();
        deps.reloaded();
        deps.adminLog().entry(player, "reload").write();
        deps.messages().sendMessage(player, "companyadmin.reload.done");
    }
}
