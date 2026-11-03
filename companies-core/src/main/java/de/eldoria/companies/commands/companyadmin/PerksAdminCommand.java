/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.PerkIds;
import de.eldoria.companies.commands.suggest.QuotedCompanyNames;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.perks.PerkService;
import de.eldoria.companies.util.Permission.Admin;
import de.eldoria.companies.util.Threading;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * {@code /companyadmin perks}: gives perks to companies and takes them away, without payment or refund.
 * <ul>
 *     <li>{@code grant <company> <perk>} gives a registered perk, offered or not;</li>
 *     <li>{@code take <company> <perk>} takes a perk, also one no longer registered.</li>
 * </ul>
 * Changes are written to the admin log. A company name with spaces is quoted.
 */
@Subcommand("perks")
@Permission(Admin.PERKS)
@PlayerOnly
public final class PerksAdminCommand {
    private static final String PREFIX = "companyadmin.perks.";

    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public PerksAdminCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Gives a company a perk.
     *
     * @param sender  sender of the command
     * @param company name of the company
     * @param perk    id of the perk
     */
    @Executes("grant")
    public void grant(
            CommandSender sender,
            @QuotedCompanyNames @StringArg(StringArgType.STRING) String company,
            @PerkIds @StringArg(StringArgType.STRING) String perk) {
        change(Senders.player(sender), company, perk, true);
    }

    /**
     * Takes a perk from a company.
     *
     * @param sender  sender of the command
     * @param company name of the company
     * @param perk    id of the perk
     */
    @Executes("take")
    public void take(
            CommandSender sender,
            @QuotedCompanyNames @StringArg(StringArgType.STRING) String company,
            @PerkIds @StringArg(StringArgType.STRING) String perk) {
        change(Senders.player(sender), company, perk, false);
    }

    private void change(Player admin, String name, String perk, boolean grant) {
        deps.requireReady();
        var perks = deps.services().perks();
        CompletableFuture.runAsync(
                        () -> {
                            var company = company(name);
                            var id = perk.toLowerCase(Locale.ROOT);
                            var outcome = grant
                                    ? perks.grant(company, id, admin.getUniqueId())
                                    : perks.take(company, id, admin.getUniqueId());
                            answer(admin, company, id, grant, outcome);
                        },
                        Threading.VIRTUAL)
                .exceptionally(err -> fail(admin, err));
    }

    private void answer(Player admin, SimpleCompany company, String perk, boolean grant, PerkService.Granted outcome) {
        var values = new TagResolver[] {Values.text("company_name", company.name()), Values.text("perk", perk)};
        switch (outcome) {
            case DONE -> {
                deps.adminLog()
                        .entry(admin, grant ? "perks grant" : "perks take")
                        .with("company", company.name())
                        .with("company_id", company.id())
                        .with("perk", perk)
                        .write();
                deps.messages().sendMessage(admin, PREFIX + (grant ? "granted" : "taken"), values);
            }
            case UNKNOWN -> deps.messages().sendError(admin, "company.perks.error.unknown", values);
            case UNCHANGED -> deps.messages().sendError(admin, PREFIX + (grant ? "owned" : "notOwned"), values);
        }
    }

    private SimpleCompany company(String name) {
        return deps.companyData()
                .retrieveCompanyByName(name)
                .asFuture()
                .join()
                .orElseThrow(() -> CommandFailure.of("error.unknownCompany"));
    }

    private Void fail(Player admin, Throwable err) {
        var cause = err.getCause() != null ? err.getCause() : err;
        if (cause instanceof CommandFailure failure) {
            failure.sendTo(admin, deps.messages());
            return null;
        }
        deps.plugin().getLogger().log(Level.SEVERE, "Could not change the perks of a company", err);
        deps.messages().sendError(admin, "error.saveFailed");
        return null;
    }
}
