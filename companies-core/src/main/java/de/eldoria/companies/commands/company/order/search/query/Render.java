/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search.query;

import de.eldoria.companies.commands.company.order.search.Query;
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

public class Render extends AdvancedCommand implements IPlayerTabExecutor {
    private final Query query;
    private final MessageBlocker messageBlocker;

    public Render(Plugin plugin, Query query, MessageBlocker messageBlocker) {
        super(plugin, CommandMeta.builder("render")
                .build());
        this.query = query;
        this.messageBlocker = messageBlocker;
    }

    public void renderSearch(Player player) {
        var playerSearch = query.getPlayerSearch(player);
        var message = MessageComposer.create().text(playerSearch.asComponent());
        if (messageBlocker.isBlocked(player)) {
            message.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>").build();
        }
        message.prependLines(25);
        messageBlocker.announce(player, "[x]");
        messageSender().sendMessage(player, message.build());
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        renderSearch(player);
    }
}
