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
 * {@code /companyadmin level create [index]}: adds a level, at the end or at a position.
 */
@Subcommand("create")
public final class Create {
    private final LevelEditor editor;

    /**
     * @param editor runs the level editor
     */
    public Create(LevelEditor editor) {
        this.editor = editor;
    }

    /**
     * Adds a level at the end.
     *
     * @param sender sender of the command
     */
    @Executes
    public void create(CommandSender sender) {
        create(sender, Integer.MAX_VALUE);
    }

    /**
     * Adds a level at a position.
     *
     * @param sender sender of the command
     * @param index  position of the new level
     */
    @Executes
    public void create(CommandSender sender, @LevelNumbers int index) {
        var player = Senders.player(sender);
        editor.info(player, editor.create(index));
    }
}
