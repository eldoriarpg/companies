/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.NodeType;
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
import java.util.List;

public class Create extends AdvancedCommand implements IPlayerTabExecutor {
    private final Configuration configuration;
    private final Info info;

    public Create(Plugin plugin, Configuration configuration, Info info) {
        super(plugin, CommandMeta.builder("create")
                .addArgument("words.index", false)
                .build());
        this.configuration = configuration;
        this.info = info;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        CommandAssertions.isTrue(configuration.nodeSettings().nodeType() == NodeType.PRIMARY, "error.secondarynode");
        var position = arguments.asInt(0, Integer.MAX_VALUE);

        var level = configuration.companySettings()
                                 .createLevel(position);
        configuration.save();
        info.show(player, level);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) {
        if (args.sizeIs(1)) {
            if (args.asString(0)
                    .isEmpty()) {
                return Collections.singletonList(localizer().localize("words.index"));
            }
            return Completion.completeInt(args.asString(0), 1, configuration.companySettings()
                                                                            .level()
                                                                            .size() + 1);
        }
        return Collections.emptyList();
    }
}
