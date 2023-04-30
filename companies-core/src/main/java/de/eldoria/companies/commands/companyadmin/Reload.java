/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.util.Permission;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class Reload extends AdvancedCommand implements IPlayerTabExecutor {
    private final Configuration configuration;

    public Reload(Plugin plugin, Configuration configuration) {
        super(plugin, CommandMeta.builder("reload")
                .withPermission(Permission.Admin.RELOAD)
                .build());
        this.configuration = configuration;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) throws CommandException {
        configuration.reload();
        messageSender().sendMessage(player, "companyadmin.reload.done");
    }
}
