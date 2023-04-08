/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.companyadmin.level.*;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.util.Permission;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import org.bukkit.plugin.Plugin;

public class Level extends AdvancedCommand {
    public Level(Plugin plugin, Configuration configuration, MessageBlocker messageBlocker) {
        super(plugin, CommandMeta.builder("level")
                .buildSubCommands((commands, builder) -> {
                    var list = new List(plugin, configuration, messageBlocker);
                    var info = new Info(plugin, configuration, messageBlocker);
                    builder.withDefaultCommand(list);
                    commands.add(new Create(plugin, configuration, info));
                    commands.add(new Edit(plugin, configuration, info));
                    commands.add(list);
                    commands.add(new Move(plugin, configuration, list));
                    commands.add(info);
                    commands.add(new Remove(plugin, configuration, list));
                })
                .withPermission(Permission.Admin.LEVEL)
                .build());
    }
}
