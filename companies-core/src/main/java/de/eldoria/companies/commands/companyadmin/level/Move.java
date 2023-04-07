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

import java.util.Collections;

public class Move extends AdvancedCommand implements IPlayerTabExecutor {
    private final Configuration configuration;
    private final List list;

    public Move(Plugin plugin, Configuration configuration, List list) {
        super(plugin, CommandMeta.builder("move")
                .addArgument("words.source", true)
                .addArgument("words.target", true)
                .build());
        this.configuration = configuration;
        this.list = list;
    }

    @Override
    public void onCommand(@NotNull Player sender, @NotNull String label, @NotNull Arguments args) throws CommandException {
        var optLevel = configuration.companySettings().level(args.asInt(0));
        CommandAssertions.isTrue(optLevel.isPresent(), "error.invalidLevel");
        configuration.companySettings().moveLevel(args.asInt(0), args.asInt(1));
        configuration.save();
        list.sendList(sender);
    }

    @Override
    public java.util.@Nullable List<String> onTabComplete(@NotNull Player sender, @NotNull String alias, @NotNull Arguments args) {
        if (args.sizeIs(1)) {
            if (args.asString(0).isEmpty()) {
                return Collections.singletonList(localizer().localize("words.source"));
            }
            return Completion.completeInt(args.asString(0), 1, configuration.companySettings().level().size());
        }

        if (args.sizeIs(2)) {
            if (args.asString(0).isEmpty()) {
                return Collections.singletonList(localizer().localize("words.target"));
            }
            return Completion.completeInt(args.asString(0), 1, configuration.companySettings().level().size());
        }
        return Collections.emptyList();
    }
}
