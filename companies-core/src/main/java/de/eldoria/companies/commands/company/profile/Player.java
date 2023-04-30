/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.profile;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.eldoutilities.commands.Completion;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

public class Player extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final Configuration configuration;
    private final MessageBlocker messageBlocker;

    public Player(Plugin plugin, ACompanyData companyData, Configuration configuration, MessageBlocker messageBlocker) {
        super(plugin, CommandMeta.builder("player")
                .build());
        this.companyData = companyData;
        this.configuration = configuration;
        this.messageBlocker = messageBlocker;
    }

    @Override
    public void onCommand(org.bukkit.entity.@NotNull Player player, @NotNull String alias, @NotNull Arguments args) throws CommandException {
        var target = args.asPlayer(0);

        companyData.retrievePlayerCompanyProfile(target)
                   .asFuture()
                   .exceptionally(err -> {
                       plugin().getLogger()
                               .log(Level.SEVERE, "Something went wrong", err);
                       return Optional.empty();
                   })
                   .thenAccept(optCompany -> {
                       if (optCompany.isEmpty()) {
                           messageSender().sendErrorActionBar(player, "error.noCompany");
                           return;
                       }
                       messageBlocker.blockPlayer(player);

                       var companyProfile = optCompany.get();
                       var builder = MessageComposer.create()
                                                    .text(companyProfile.asExternalProfileComponent(configuration));

                       if (messageBlocker.isBlocked(player)) {
                           builder.newLine()
                                  .text("<click:run_command:/company chatblock false><red>[x]</red></click>");
                       }
                       messageBlocker.announce(player, "[x]");
                       builder.prependLines(25);
                       messageSender().sendMessage(player, builder);
                   });
    }

    @Override
    public @Nullable List<String> onTabComplete(org.bukkit.entity.@NotNull Player player, @NotNull String alias, @NotNull Arguments args) {
        if (args.size() == 1) {
            return Completion.completeOnlinePlayers(args.asString(0));
        }
        return Collections.emptyList();
    }
}
