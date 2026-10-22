/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.data.wrapper.order.ReturnKind;
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
 * Hands out items of received orders which did not fit into the player's inventory.
 */
public class Collect extends AdvancedCommand implements IPlayerTabExecutor {
    private final ItemReturnService itemReturnService;

    public Collect(Plugin plugin, ItemReturnService itemReturnService) {
        super(plugin, CommandMeta.builder("collect").build());
        this.itemReturnService = itemReturnService;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments)
            throws CommandException {
        itemReturnService.handOut(player, ReturnKind.OWNER, false);
    }
}
