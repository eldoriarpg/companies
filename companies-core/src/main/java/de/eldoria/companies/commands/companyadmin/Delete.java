/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.CompanyNames;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.util.Permission.Admin;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;

import java.util.logging.Level;

/**
 * {@code /companyadmin delete <name>}: dissolves a company.
 */
@Subcommand("delete")
@Permission(Admin.DELETE)
@PlayerOnly
public final class Delete {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public Delete(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * @param sender sender of the command
     * @param name   name of the company
     */
    @Executes
    public void delete(CommandSender sender, @CompanyNames @StringArg(StringArgType.GREEDY) String name) {
        var player = Senders.player(sender);
        var disbandService = deps.services().disbandService();
        var companyData = deps.companyData();

        companyData
                .retrieveCompanyByName(name)
                .asFuture()
                .thenAccept(company -> {
                    if (company.isEmpty()) {
                        messages().sendError(player, "error.unknownCompany");
                        return;
                    }

                    var profile =
                            companyData.retrieveCompanyProfile(company.get()).join();
                    if (profile == null || profile.isEmpty()) {
                        messages().sendError(player, "error.unknownCompany");
                        return;
                    }
                    if (!disbandService.disband(profile.get())) {
                        messages().sendError(player, "company.disband.failed");
                        return;
                    }
                    deps.adminLog()
                            .entry(player, "delete")
                            .with("company", profile.get().name())
                            .with("company_id", profile.get().id())
                            .write();
                    messages().sendMessage(player, "company.disband.disbanded");
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    private MessageSender messages() {
        return deps.messages();
    }
}
