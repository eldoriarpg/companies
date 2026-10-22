/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.commands.suggest.LevelNumbers;
import de.eldoria.companies.commands.support.Senders;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;

/**
 * {@code /companyadmin level move <source> <target>}: moves a level to another position.
 */
@Subcommand("move")
public final class Move {
    private final LevelEditor editor;

    /**
     * @param editor runs the level editor
     */
    public Move(LevelEditor editor) {
        this.editor = editor;
    }

    /**
     * @param sender sender of the command
     * @param source number of the level to move
     * @param target position it moves to
     */
    @Executes
    public void move(CommandSender sender, @LevelNumbers int source, @LevelNumbers int target) {
        var player = Senders.player(sender);
        editor.move(source, target);
        editor.list(player);
    }
}
