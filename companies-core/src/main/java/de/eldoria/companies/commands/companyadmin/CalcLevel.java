/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.util.Permission.Admin;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /companyadmin calclevel}: recalculates the level of every company.
 */
@Subcommand("calclevel")
@Permission(Admin.CALC_LEVEL)
public final class CalcLevel {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public CalcLevel(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Starts the recalculation and tells a player sender when it is done.
     *
     * @param sender sender of the command
     */
    @Executes
    public void calcLevel(CommandSender sender) {
        deps.services().levelService().updateAllCompanies(() -> {
            if (sender instanceof Player player) {
                var offlinePlayer = Bukkit.getOfflinePlayer(player.getUniqueId());
                if (offlinePlayer.isOnline()) {
                    deps.messages().sendMessage(sender, "companyadmin.calcLevel.done");
                }
            }
        });
        deps.messages().sendMessage(sender, "companyadmin.calcLevel.start");
    }
}
