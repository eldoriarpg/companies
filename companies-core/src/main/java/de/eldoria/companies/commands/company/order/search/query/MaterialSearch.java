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
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MaterialSearch extends AdvancedCommand implements IPlayerTabExecutor {
    private final Query query;

    public MaterialSearch(Plugin plugin, Query query) {
        super(plugin, CommandMeta.builder("material_search")
                .addUnlocalizedArgument("any|all", true)
                .build());
        this.query = query;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        var searchType = arguments.asString(0);
        CommandAssertions.isTrue(Completion.isCommand(searchType, "all", "any"), "error.invalidSearchType");
        query.getPlayerSearch(player)
             .anyMaterial("any".equalsIgnoreCase(searchType));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return Completion.complete(arguments.asString(0), "any", "all");
    }
}
