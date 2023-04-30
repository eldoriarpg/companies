/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.profile;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
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

import java.util.Optional;
import java.util.logging.Level;

public class Id extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final Configuration configuration;
    private final MessageBlocker messageBlocker;

    public Id(Plugin plugin, ACompanyData companyData, Configuration configuration, MessageBlocker messageBlocker) {
        super(plugin, CommandMeta.builder("id")
                .addArgument("words.id", true)
                .build());
        this.companyData = companyData;
        this.configuration = configuration;
        this.messageBlocker = messageBlocker;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        var optionalInt = arguments.asInt(0);

        companyData.retrieveCompanyById(optionalInt)
                   .asFuture()
                   .exceptionally(err -> {
                       plugin().getLogger()
                               .log(Level.SEVERE, "Something went wrong", err);
                       return Optional.empty();
                   })
                   .thenAccept(optComp -> {
                       if (optComp.isEmpty()) {
                           messageSender().sendErrorActionBar(player, "error.unknownCompany");
                           return;
                       }
                       var optProfile = companyData.retrieveCompanyProfile(optComp.get())
                                                   .asFuture()
                                                   .exceptionally(err -> {
                                                       plugin().getLogger()
                                                               .log(Level.SEVERE, "Something went wrong", err);
                                                       return Optional.empty();
                                                   })
                                                   .join();
                       if (optProfile.isEmpty()) return;
                       var builder = MessageComposer.create()
                                                    .text(optProfile.get().asExternalProfileComponent(configuration));
                       if (messageBlocker.isBlocked(player)) {
                           builder.newLine()
                                  .text("<click:run_command:/company chatblock false><red>[x]</red></click>");
                       }
                       messageBlocker.announce(player, "[x]");
                       builder.prependLines(25);
                       messageSender().sendMessage(player, builder);
                   })
                   .exceptionally(err -> {
                       plugin().getLogger()
                               .log(Level.SEVERE, "Something went wrong", err);
                       return null;
                   });
    }
}
