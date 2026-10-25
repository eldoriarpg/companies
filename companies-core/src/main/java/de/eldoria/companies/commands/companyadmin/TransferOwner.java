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
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.services.ActionResult;
import de.eldoria.companies.services.AdminCompanyService;
import de.eldoria.companies.util.Permission.Admin;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.messages.Replacement;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;

import java.util.concurrent.CompletableFuture;

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
        var companies = new AdminCompanyService(deps.companyData(), deps.companyChanges());
        CompletableFuture.supplyAsync(
                        () -> companies
                                .profile(company)
                                .map(profile -> companies
                                        .transferOwner(profile, target.getUniqueId())
                                        .map(transfer -> new Done(profile, transfer)))
                                .orElseGet(() -> ActionResult.refusedChat("error.unknownCompany")),
                        Threading.VIRTUAL)
                .thenAccept(result -> {
                    switch (result) {
                        case ActionResult.Refused<Done> refused ->
                            refused.failure().sendTo(admin, messages());
                        case ActionResult.Done<Done> done -> {
                            var transfer = done.value().transfer();
                            deps.adminLog()
                                    .entry(admin, "transferowner")
                                    .with("company", done.value().profile().name())
                                    .with("company_id", done.value().profile().id())
                                    .with("from", transfer.from().player().getName())
                                    .with("to", transfer.to().player().getName())
                                    .write();
                            messages()
                                    .sendMessage(
                                            admin,
                                            "companyadmin.transferOwner.done",
                                            Replacement.create(
                                                    "name",
                                                    transfer.to().player().getName()));
                        }
                    }
                });
    }

    /**
     * A transfer and the company it happened in.
     *
     * @param profile  the company
     * @param transfer the former and the new owner
     */
    private record Done(CompanyProfile profile, AdminCompanyService.Transfer transfer) {}

    private MessageSender messages() {
        return deps.messages();
    }
}
