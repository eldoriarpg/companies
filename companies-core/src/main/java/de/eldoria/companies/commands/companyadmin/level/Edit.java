/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.commands.suggest.LevelFields;
import de.eldoria.companies.commands.suggest.LevelNumbers;
import de.eldoria.companies.commands.suggest.LevelValues;
import de.eldoria.companies.commands.support.Senders;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import org.bukkit.command.CommandSender;

/**
 * {@code /companyadmin level edit <level> <field> <value>}: changes one field of a level.
 */
@Subcommand("edit")
public final class Edit {
    private final LevelEditor editor;

    /**
     * @param editor runs the level editor
     */
    public Edit(LevelEditor editor) {
        this.editor = editor;
    }

    /**
     * @param sender sender of the command
     * @param level  number of the level
     * @param field  field to change
     * @param value  new value
     */
    @Executes
    public void edit(
            CommandSender sender,
            @LevelNumbers int level,
            @LevelFields String field,
            @LevelValues @StringArg(StringArgType.GREEDY) String value) {
        var player = Senders.player(sender);
        var companyLevel = editor.level(level);
        editor.edit(player, companyLevel, field, value);
        editor.info(player, companyLevel);
    }
}
