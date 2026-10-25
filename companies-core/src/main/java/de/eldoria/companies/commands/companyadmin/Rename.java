/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.QuotedCompanyNames;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.services.ActionResult;
import de.eldoria.companies.services.AdminCompanyService;
import de.eldoria.companies.util.Permission.Admin;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;

/**
 * {@code /companyadmin rename <company> <name>}: renames a company. A current name with spaces is quoted.
 */
@Subcommand("rename")
@Permission(Admin.RENAME)
@PlayerOnly
public final class Rename {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public Rename(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * @param sender  sender of the command
     * @param current current name of the company
     * @param name    new name of the company
     */
    @Executes
    public void rename(
            CommandSender sender,
            @QuotedCompanyNames @StringArg(StringArgType.STRING) String current,
            @StringArg(StringArgType.GREEDY) String name) {
        var player = Senders.player(sender);
        deps.requireReady();
        var companyData = deps.companyData();
        var companies = new AdminCompanyService(companyData, deps.companyChanges());
        companyData.retrieveCompanyByName(current).asFuture().thenAccept(company -> {
            if (company.isEmpty()) {
                messages().sendError(player, "error.unknownCompany");
                return;
            }
            switch (companies.rename(company.get(), name, player.getUniqueId())) {
                case ActionResult.Refused<String> refused -> refused.failure().sendTo(player, messages());
                case ActionResult.Done<String> done -> {
                    deps.adminLog()
                            .entry(player, "rename")
                            .with("company_id", company.get().id())
                            .with("from", current)
                            .with("to", name)
                            .write();
                    messages().sendMessage(player, "company.rename.changed");
                }
            }
        });
    }

    private MessageSender messages() {
        return deps.messages();
    }
}
