/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.commands.suggest.LevelNumbers;
import de.eldoria.companies.commands.support.Senders;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;

/**
 * {@code /companyadmin level remove <level>}: removes a level at once. The menus ask for a confirmation first.
 */
@Subcommand("remove")
public final class Remove {
    private final LevelEditor editor;

    /**
     * @param editor runs the level editor
     */
    public Remove(LevelEditor editor) {
        this.editor = editor;
    }

    /**
     * @param sender sender of the command
     * @param level  number of the level
     */
    @Executes
    public void remove(CommandSender sender, @LevelNumbers int level) {
        var player = Senders.player(sender);
        editor.remove(player, level);
        editor.list(player);
    }
}
