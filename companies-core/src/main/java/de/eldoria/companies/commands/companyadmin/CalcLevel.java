/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.services.LevelService;
import de.eldoria.companies.util.Permission;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.ITabExecutor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class CalcLevel extends AdvancedCommand implements ITabExecutor {
    private final LevelService levelService;

    public CalcLevel(Plugin plugin, LevelService levelService) {
        super(plugin, CommandMeta.builder("calcLevel")
                .withPermission(Permission.Admin.CALC_LEVEL)
                .build());
        this.levelService = levelService;
    }

    @Override
    public void onCommand(@NotNull CommandSender sender, @NotNull String alias, @NotNull Arguments args) throws CommandException {
        levelService.updateAllCompanies(() -> {
            if (sender instanceof Player) {
                var offlinePlayer = Bukkit.getOfflinePlayer(((Entity) sender).getUniqueId());
                if (offlinePlayer.isOnline()) {
                    messageSender().sendMessage(sender, "companyadmin.calcLevel.done");
                }
            }
        });
        messageSender().sendMessage(sender, "companyadmin.calcLevel.start");
    }
}
