/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search.query;

import de.eldoria.companies.commands.company.order.search.Query;
import de.eldoria.eldoutilities.commands.Completion;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Argument;
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

public class Material extends AdvancedCommand implements IPlayerTabExecutor {
    private final Query query;

    public Material(Plugin plugin, Query query) {
        super(plugin, CommandMeta.builder("material_add")
                .addAlias("material_remove")
                .addArgument("words.material", false)
                .build());
        this.query = query;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        if ("material_add".equalsIgnoreCase(label)) {
            CommandAssertions.invalidArguments(meta(), arguments, Argument.input("words.material", true));
            query.getPlayerSearch(player)
                 .materials()
                 .add(arguments.join("_"));
            return;
        }

        var search = query.getPlayerSearch(player);
        if (arguments.isEmpty()) {
            search.materials()
                  .clear();
            return;
        }
        search.materials()
              .remove(arguments.join("_"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        if (Completion.isCommand(alias, "material_add")) {
            return Completion.completeMaterial(arguments.join("_"), true);
        }
        if (Completion.isCommand(alias, "material_remove")) {
            if (arguments.size() == 1) {
                return Collections.emptyList();
            }
            var playerSearch = query.getPlayerSearch(player);
            return Completion.complete(arguments.asString(1), playerSearch.materials());
        }
        return Collections.emptyList();
    }
}
