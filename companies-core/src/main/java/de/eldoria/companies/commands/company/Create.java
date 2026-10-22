/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.Checks;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.menus.ConfirmFlow;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Permission.Company;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.messages.Replacement;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * {@code /company create <name>}: founds a company for the founding price after the founder confirmed it.
 */
@Subcommand("create")
@Permission(Company.CREATE)
@PlayerOnly
public final class Create {
    public static final int MAX_NAME_LENGTH = 32;
    private final CommandDependencies deps;
    private final ConfirmFlow<String> registrations;

    /**
     * @param deps what the commands need
     */
    public Create(CommandDependencies deps) {
        this.deps = deps;
        this.registrations = ConfirmFlow.create(deps.plugin(), this::found);
    }

    /**
     * Asks the sender to confirm founding a company of that name.
     *
     * @param sender sender of the command
     * @param name   name of the company
     */
    @Executes
    public void create(CommandSender sender, @StringArg(StringArgType.GREEDY) String name) {
        var player = Senders.player(sender);
        var economy = deps.services().economy();
        var menus = deps.services().menus();
        Checks.shorterThan(name, MAX_NAME_LENGTH);

        checkFree(player, name, () -> {
            var price = deps.configuration().companySettings().foundingPrice();
            var question = MenuText.create()
                    .locale(
                            "company.create.create",
                            Values.money("amount", economy, price),
                            Values.text("company_name", name));
            registrations.ask(menus, player, name, question);
        });
    }

    /**
     * Founds the company the sender asked for.
     *
     * @param sender sender of the command
     */
    @Executes("confirm")
    public void confirm(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        registrations.confirm(player);
    }

    /**
     * Drops the founding the sender was asked to confirm.
     *
     * @param sender sender of the command
     */
    @Executes("deny")
    public void deny(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        registrations.cancel(player);
    }

    private void found(Player player, String name) {
        var economy = deps.services().economy();
        checkFree(player, name, () -> foundCompany(player, name, economy));
    }

    private void checkFree(Player player, String name, Runnable free) {
        deps.companyData().retrievePlayerCompany(player).whenComplete(current -> {
            if (current.isPresent()) {
                messages().sendErrorActionBar(player, "error.alreadyMember");
                return;
            }
            deps.companyData().retrieveCompanyByName(name).whenComplete(company -> {
                if (company.isPresent()) {
                    messages().sendErrorActionBar(player, "error.companyNameUsed");
                    return;
                }
                free.run();
            });
        });
    }

    private void foundCompany(Player player, String name, Economy economy) {
        var price = deps.configuration().companySettings().foundingPrice();
        if (!economy.has(player, price)
                || economy.withdrawPlayer(player, price).type != EconomyResponse.ResponseType.SUCCESS) {
            var fallbackCurr = economy.currencyNameSingular().isBlank()
                    ? MessageComposer.escape("words.money")
                    : economy.currencyNameSingular();
            var curr = economy.currencyNamePlural().isBlank() ? fallbackCurr : economy.currencyNamePlural();
            messages()
                    .sendErrorActionBar(
                            player,
                            "error.insufficientCurrency",
                            Replacement.create("currency", curr),
                            Replacement.create("amount", price));
            return;
        }
        deps.companyData()
                .submitCompanyCreation(
                        name, deps.configuration().companySettings().joining().defaultMode())
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return -1;
                })
                .thenApply(id -> {
                    if (id == -1) return false;
                    deps.companyData()
                            .submitMemberUpdate(
                                    CompanyMember.forCompanyId(id, player).addPermission(CompanyPermission.OWNER))
                            .join();
                    return true;
                })
                .thenAcceptAsync(
                        created -> {
                            if (!created) {
                                economy.depositPlayer(player, price);
                                messages().sendErrorActionBar(player, "error.saveFailed");
                                return;
                            }
                            messages().sendMessage(player, "company.create.created");
                        },
                        Threading.mainThread(deps.plugin()));
    }

    private MessageSender messages() {
        return deps.messages();
    }
}
