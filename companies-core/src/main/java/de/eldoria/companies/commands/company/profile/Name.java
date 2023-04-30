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
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

public class Name extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final Configuration configuration;
    private final MessageBlocker messageBlocker;

    public Name(Plugin plugin, ACompanyData companyData, Configuration configuration, MessageBlocker messageBlocker) {
        super(plugin, CommandMeta.builder("name")
                .addArgument("name", true)
                .build());
        this.companyData = companyData;
        this.configuration = configuration;
        this.messageBlocker = messageBlocker;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        companyData.retrieveCompanyByName(arguments.join())
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
                                                   .join();
                       if (optProfile.isEmpty()) return;
                       messageBlocker.blockPlayer(player);
                       var companyProfile = optProfile.get();
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
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) {
        return Completion.completeFreeInput(args.asString(0), 32, localizer().localize("words.name"));
    }
}
