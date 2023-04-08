/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.util.Permission;
import de.eldoria.eldoutilities.commands.Completion;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class Rename extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;

    public Rename(Plugin plugin, ACompanyData companyData) {
        super(plugin, CommandMeta.builder("rename")
                .withPermission(Permission.Admin.RENAME)
                .addArgument("words.name", true)
                .addArgument("words.name", true)
                .build());
        this.companyData = companyData;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) throws CommandException {
        args.parseQuoted();
        CommandAssertions.invalidArguments(meta(), args);

        companyData.retrieveCompanyByName(args.asString(0))
                   .asFuture()
                   .thenAccept(company -> {
                       if (company.isEmpty()) {
                           messageSender().sendError(player, "error.unknownCompany");
                           return;
                       }

                       var other = companyData.retrieveCompanyByName(args.asString(1))
                                              .join();

                       if (other == null || other.isEmpty()) {
                           messageSender().sendError(player, "error.companyNameUsed");
                           return;
                       }

                       companyData.updateCompanyName(company.get(), args.asString(1));
                       messageSender().sendError(player, "company.rename.changed");
                   });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) {
        args.parseQuoted();

        if (args.size() == 1) {
            return Completion.completeFreeInput(args.asString(0), 32, localizer().localize("words.source"));
        }

        if (args.size() == 2) {
            return Completion.completeFreeInput(args.asString(1), 32, localizer().localize("words.target"));
        }

        return Collections.emptyList();
    }
}
