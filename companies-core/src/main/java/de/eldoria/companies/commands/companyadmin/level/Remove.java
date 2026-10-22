/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.LevelNumbers;
import de.eldoria.companies.commands.support.Checks;
import de.eldoria.companies.commands.support.Senders;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;

/**
 * {@code /companyadmin level remove <level>}: removes a level.
 */
@Subcommand("remove")
public final class Remove {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public Remove(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * @param sender sender of the command
     * @param level  number of the level
     */
    @Executes
    public void remove(CommandSender sender, @LevelNumbers int level) {
        var player = Senders.player(sender);
        var messageBlocker = deps.services().messageBlocker();
        var configuration = deps.configuration();
        Levels.requirePrimary(configuration);
        Checks.isTrue(configuration.companySettings().deleteLevel(level), "error.invalidLevel");
        configuration.save();
        LevelMenus.list(player, configuration, messageBlocker);
    }
}
