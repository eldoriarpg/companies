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
    private final CommandDependencies deps;

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
        this.deps = deps;
        create = new Create(deps);
        edit = new Edit(deps);
        info = new Info(deps);
        move = new Move(deps);
        remove = new Remove(deps);
    }

    /**
     * Lists the levels.
     *
     * @param sender sender of the command
     */
    @Executes
    @Executes("list")
    public void list(CommandSender sender) {
        var player = Senders.player(sender);
        LevelMenus.list(player, deps.configuration(), deps.services().menus());
    }
}
