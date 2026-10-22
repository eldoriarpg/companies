/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.util.Permission.Admin;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;

/**
 * {@code /companyadmin level}: the editor of the company levels. Without a subcommand it lists the levels.
 */
@Subcommand("level")
@Permission(Admin.LEVEL)
@PlayerOnly
public final class LevelCommand {
    private final LevelEditor editor;

    @Subcommand
    public final Create create;

    @Subcommand
    public final Edit edit;

    @Subcommand
    public final Info info;

    @Subcommand
    public final Move move;

    @Subcommand
    public final Remove remove;

    /**
     * @param deps what the commands need
     */
    public LevelCommand(CommandDependencies deps) {
        editor = new LevelEditor(deps);
        create = new Create(editor);
        edit = new Edit(editor);
        info = new Info(editor);
        move = new Move(editor);
        remove = new Remove(editor);
    }

    /**
     * Lists the levels.
     *
     * @param sender sender of the command
     */
    @Executes
    @Executes("list")
    public void list(CommandSender sender) {
        editor.list(Senders.player(sender));
    }
}
