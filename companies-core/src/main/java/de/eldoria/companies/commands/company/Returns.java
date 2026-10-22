/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Hands out items which were returned to the player after an order they delivered to was released.
 */
public class Returns extends AdvancedCommand implements IPlayerTabExecutor {
    private final ItemReturnService itemReturnService;

    public Returns(Plugin plugin, ItemReturnService itemReturnService) {
        super(plugin, CommandMeta.builder("returns").build());
        this.itemReturnService = itemReturnService;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments)
            throws CommandException {
        itemReturnService.handOut(player, false);
    }
}
