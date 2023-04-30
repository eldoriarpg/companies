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
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.messages.Replacement;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class TransferOwner extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;

    public TransferOwner(Plugin plugin, ACompanyData companyData) {
        super(plugin, CommandMeta.builder("transferOwner")
                .addArgument("words.name", true)
                .addArgument("words.target", true)
                .withPermission(Permission.Admin.TRANSFER_OWNER)
                .build());
        this.companyData = companyData;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) throws CommandException {
        args.parseQuoted();
        companyData.retrieveCompanyByName(args.asString(0))
                   .asFuture()
                   .thenAccept(optCompany -> {
                       if (optCompany.isEmpty()) {
                           messageSender().sendError(player, "error.unknownCompany");
                           return;
                       }

                       var company = companyData.retrieveCompanyProfile(optCompany.get())
                                                .join()
                                                .get();

                       var owner = company.owner();
                       owner.isOwner(false);

                       OfflinePlayer target;
                       try {
                           target = args.asOfflinePlayer(1);
                       } catch (CommandException e) {
                           messageSender().sendError(player, e.getMessage(), e.replacements());
                           return;
                       }

                       var newOwner = company.member(target);
                       if (newOwner.isEmpty()) {
                           messageSender().sendError(player, "error.noCompanyMember");
                           return;
                       }

                       newOwner.get()
                               .isOwner(true);
                       companyData.submitMemberUpdate(newOwner.get());
                       companyData.submitMemberUpdate(owner);
                       messageSender().sendMessage(player, "companyadmin.transferOwner.done",
                               Replacement.create("name", newOwner.get()
                                                                  .player()
                                                                  .getName()));
                   });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) {
        args.parseQuoted();
        if (args.size() == 1) {
            var name = args.asString(0);
            return Completion.completeFreeInput(name, 34, localizer().localize("words.name"));
        }

        if (args.size() == 2) {
            return Completion.completePlayers(args.asString(1));
        }
        return Collections.emptyList();
    }
}
