/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.eldoutilities.commands.Completion;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class Remove extends AdvancedCommand implements IPlayerTabExecutor {
    private final Configuration configuration;
    private final List list;

    public Remove(Plugin plugin, Configuration configuration, List list) {
        super(plugin, CommandMeta.builder("remove")
                .addArgument("words.level", true)
                .build());
        this.configuration = configuration;
        this.list = list;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) throws CommandException {
        var level = args.asInt(0);
        var success = configuration.companySettings()
                                   .deleteLevel(level);
        CommandAssertions.isTrue(success, "error.invalidLevel");
        configuration.save();
        list.sendList(player);
    }

    @Override
    public @Nullable java.util.List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) {
        return Completion.completeInt(args.asString(0), 1, configuration.companySettings()
                                                                        .level()
                                                                        .size());
    }
}
