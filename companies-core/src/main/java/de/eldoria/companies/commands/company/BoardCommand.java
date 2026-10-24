/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.company.order.search.OrderSearch;
import de.eldoria.companies.commands.suggest.BoardPresets;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.util.Permission.Boards;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /company board [preset]}: opens the order search filtered by a board preset, by materials, or showing every
 * open order. Signs open the same view, and NPC and menu plugins can run this command for the player.
 */
@Subcommand("board")
@Permission(Boards.USE)
@PlayerOnly
public final class BoardCommand {
    private final CommandDependencies deps;

    /**
     * Binds the search as the view of every board.
     *
     * @param deps   what the commands need
     * @param search the order search showing the results
     */
    public BoardCommand(CommandDependencies deps, OrderSearch search) {
        this.deps = deps;
        deps.boards().bind((player, query) -> {
            deps.requireReady();
            deps.searches().replace(player.getUniqueId(), query);
            search.showPage(player, 0);
        });
    }

    /**
     * Shows every open order.
     *
     * @param sender sender of the command
     */
    @Executes
    public void all(CommandSender sender) {
        open(sender, null);
    }

    /**
     * Shows the orders of a preset or of materials.
     *
     * @param sender sender of the command
     * @param board  preset name or materials separated by spaces
     */
    @Executes
    public void board(CommandSender sender, @BoardPresets @StringArg(StringArgType.GREEDY) String board) {
        open(sender, board);
    }

    private void open(CommandSender sender, @Nullable String board) {
        var player = Senders.player(sender);
        var boards = deps.boards();
        boards.open(player, boards.require(board));
    }
}
