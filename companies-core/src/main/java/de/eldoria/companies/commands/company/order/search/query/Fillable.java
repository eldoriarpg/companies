/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search.query;

import de.eldoria.companies.commands.company.order.search.Query;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Switches the filter which only shows orders the player can deliver items to.
 */
public class Fillable extends AdvancedCommand implements IPlayerTabExecutor {
    private final Query query;

    public Fillable(Plugin plugin, Query query) {
        super(
                plugin,
                CommandMeta.builder("fillable")
                        .addUnlocalizedArgument("true|false", true)
                        .build());
        this.query = query;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments)
            throws CommandException {
        query.getPlayerSearch(player).fillable(arguments.asBoolean(0));
    }
}
