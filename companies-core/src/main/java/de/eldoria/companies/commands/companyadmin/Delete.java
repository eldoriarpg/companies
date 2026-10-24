/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.CompanyNames;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.menus.ConfirmFlow;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Permission.Admin;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * {@code /companyadmin delete <name>}: dissolves a company after the admin confirmed it.
 * <p>
 * The question names the company, its members, the orders it works on and its treasury. It is valid for 30 seconds
 * and confirmed with the button or {@code /companyadmin delete confirm}.
 */
@Subcommand("delete")
@Permission(Admin.DELETE)
@PlayerOnly
public final class Delete {
    private final CommandDependencies deps;
    private final ConfirmFlow<Integer> deletions;

    /**
     * @param deps what the commands need
     */
    public Delete(CommandDependencies deps) {
        this.deps = deps;
        deletions = ConfirmFlow.create(deps.plugin(), this::deleteConfirmed);
    }

    /**
     * Asks to dissolve a company.
     *
     * @param sender sender of the command
     * @param name   name of the company
     */
    @Executes
    public void delete(CommandSender sender, @CompanyNames @StringArg(StringArgType.GREEDY) String name) {
        var player = Senders.player(sender);
        var services = deps.services();
        deps.companyData()
                .retrieveCompanyByName(name)
                .asFuture()
                .thenCompose(this::profile)
                .thenCompose(profile -> {
                    if (profile.isEmpty()) {
                        messages().sendError(player, "error.unknownCompany");
                        return CompletableFuture.completedFuture(null);
                    }
                    var company = profile.get();
                    var orders =
                            deps.orderData().retrieveCompanyOrderCount(company).asFuture();
                    var treasury = services.treasury().treasury(company.id());
                    return orders.thenCombine(treasury, (open, funds) -> {
                        deletions.ask(
                                services.menus(),
                                player,
                                company.id(),
                                MenuText.create()
                                        .locale(
                                                "companyadmin.delete.confirm",
                                                Values.text("company_name", company.name()),
                                                Values.number(
                                                        "members",
                                                        company.members().size()),
                                                Values.number("orders", open),
                                                Values.money(
                                                        "balance",
                                                        services.economy(),
                                                        funds.balance().doubleValue())));
                        return null;
                    });
                })
                .exceptionally(err -> fail(err));
    }

    /**
     * Dissolves the company the sender was asked about.
     *
     * @param sender sender of the command
     */
    @Executes("confirm")
    public void confirm(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        deletions.confirm(player);
    }

    private void deleteConfirmed(Player player, int companyId) {
        var disbandService = deps.services().disbandService();
        deps.companyData()
                .retrieveCompanyById(companyId)
                .asFuture()
                .thenCompose(this::profile)
                .thenAccept(profile -> {
                    if (profile.isEmpty()) {
                        messages().sendError(player, "error.unknownCompany");
                        return;
                    }
                    if (!disbandService.disband(profile.get(), player.getUniqueId())) {
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
                .exceptionally(err -> fail(err));
    }

    private CompletableFuture<Optional<CompanyProfile>> profile(Optional<SimpleCompany> company) {
        if (company.isEmpty()) return CompletableFuture.completedFuture(Optional.empty());
        return deps.companyData()
                .retrieveCompanyProfile(company.get())
                .asFuture()
                .thenApply(profile -> profile == null ? Optional.empty() : profile);
    }

    private Void fail(Throwable err) {
        deps.plugin().getLogger().log(Level.SEVERE, "Could not delete a company", err);
        return null;
    }

    private MessageSender messages() {
        return deps.messages();
    }
}
