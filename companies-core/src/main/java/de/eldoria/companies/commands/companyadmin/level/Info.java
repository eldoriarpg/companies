/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.LevelNumbers;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.Senders;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;

/**
 * {@code /companyadmin level info <level>}: the editor of one level.
 */
@Subcommand("info")
public final class Info {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public Info(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * @param sender sender of the command
     * @param level  number of the level
     */
    @Executes
    public void info(CommandSender sender, @LevelNumbers int level) {
        var player = Senders.player(sender);
        var menus = deps.services().menus();
        var companyLevel = deps.configuration()
                .companySettings()
                .level(level)
                .orElseThrow(() -> CommandFailure.actionBar("error.invalidLevel"));
        LevelMenus.info(player, companyLevel, menus);
    }
}
