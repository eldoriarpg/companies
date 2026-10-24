/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.KnownPlayers;
import de.eldoria.companies.commands.suggest.QuotedCompanyNames;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.util.Permission.Admin;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.messages.Replacement;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;

/**
 * {@code /companyadmin transferowner <company> <player>}: makes a member the owner of a company. The player may be
 * offline if the server knows them.
 */
@Subcommand("transferowner")
@Permission(Admin.TRANSFER_OWNER)
@PlayerOnly
public final class TransferOwner {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public TransferOwner(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * @param sender  sender of the command
     * @param company name of the company
     * @param player  name of the new owner
     */
    @Executes
    public void transferOwner(
            CommandSender sender,
            @QuotedCompanyNames @StringArg(StringArgType.STRING) String company,
            @KnownPlayers String player) {
        var admin = Senders.player(sender);
        deps.requireReady();
        var target = deps.plugin().getServer().getOfflinePlayerIfCached(player);
        if (target == null) throw CommandFailure.of("error.unknownPlayer");
        var companyData = deps.companyData();

        companyData.retrieveCompanyByName(company).asFuture().thenAccept(optCompany -> {
            if (optCompany.isEmpty()) {
                messages().sendError(admin, "error.unknownCompany");
                return;
            }

            var optProfile =
                    companyData.retrieveCompanyProfile(optCompany.get()).join();
            if (optProfile == null || optProfile.isEmpty()) {
                messages().sendError(admin, "error.unknownCompany");
                return;
            }
            var profile = optProfile.get();

            var owner = profile.owner();
            owner.isOwner(false);

            var newOwner = profile.member(target);
            if (newOwner.isEmpty()) {
                messages().sendError(admin, "error.noCompanyMember");
                return;
            }

            newOwner.get().isOwner(true);
            companyData.submitMemberUpdate(newOwner.get()).join();
            companyData.submitMemberUpdate(owner).join();
            if (!owner.uuid().equals(newOwner.get().uuid())) {
                deps.companyChanges()
                        .ownerTransferred(
                                profile.id(), owner.uuid(), newOwner.get().uuid(), true);
            }
            deps.adminLog()
                    .entry(admin, "transferowner")
                    .with("company", profile.name())
                    .with("company_id", profile.id())
                    .with("from", owner.player().getName())
                    .with("to", newOwner.get().player().getName())
                    .write();
            messages()
                    .sendMessage(
                            admin,
                            "companyadmin.transferOwner.done",
                            Replacement.create("name", newOwner.get().player().getName()));
        });
    }

    private MessageSender messages() {
        return deps.messages();
    }
}
