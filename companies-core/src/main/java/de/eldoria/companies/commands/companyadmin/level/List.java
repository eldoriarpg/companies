/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class List extends AdvancedCommand implements IPlayerTabExecutor {
    private final Configuration configuration;
    private final MessageBlocker messageBlocker;

    public List(Plugin plugin, Configuration configuration, MessageBlocker messageBlocker) {
        super(plugin, CommandMeta.builder("list")
                .build());
        this.configuration = configuration;
        this.messageBlocker = messageBlocker;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        sendList(player);
    }

    public void sendList(Player player) {
        var level = new ArrayList<String>();
        messageBlocker.blockPlayer(player);
        for (var companyLevel : configuration.companySettings()
                                             .level()) {
            var info = MessageComposer.create()
                                      .text("<hover:show_text:'%s'><name>%s - <value>%s</hover>", companyLevel.asComponent(), companyLevel.level(), companyLevel.levelName())
                                      .space()
                                      .text("<click:run_command:/companyadmin level info %s><show>[", companyLevel.level())
                                      .localeCode("words.info")
                                      .text("]</click>")
                                      .space()
                                      .text("<click:run_command:/companyadmin level remove %s><remove>[", companyLevel.level())
                                      .localeCode("words.remove")
                                      .text("]</click>")
                                      .space()
                                      .text("<click:suggest_command:/companyadmin level move %s ><modify>[", companyLevel.level())
                                      .localeCode("words.move")
                                      .text("]</click>")
                                      .build();
            level.add(info);
        }
        var builder = MessageComposer.create()
                                     .text("<heading>")
                                     .localeCode("words.level")
                                     .text(" <click:suggest_command:/companyadmin level create ><add>[")
                                     .localeCode("words.create")
                                     .text("]</click>")
                                     .newLine()
                                     .text(String.join("\n", level));
        if (messageBlocker.isBlocked(player)) {
            builder.newLine()
                   .text("<click:run_command:/company chatblock false><red>[x]</red></click>");
        }
        messageBlocker.announce(player, "[x]");
        builder.prependLines(25);

        messageSender().sendMessage(player, builder);
    }
}
