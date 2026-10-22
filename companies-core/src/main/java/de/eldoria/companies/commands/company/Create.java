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
import de.eldoria.companies.util.Confirmations;
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
    private final Confirmations<String> registrations = new Confirmations<>();

    /**
     * @param deps what the commands need
     */
    public Create(CommandDependencies deps) {
        this.deps = deps;
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
        Checks.shorterThan(name, MAX_NAME_LENGTH);

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
                requestConfirmation(player, name, economy);
            });
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
        var economy = deps.services().economy();
        var optName = registrations.confirm(player.getUniqueId());
        if (optName.isEmpty()) {
            messages().sendErrorActionBar(player, "error.confirmationExpired");
            return;
        }
        var name = optName.get();

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
                foundCompany(player, name, economy);
            });
        });
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
        registrations.confirm(player.getUniqueId());
        messages().sendMessage(player, "words.canceled");
    }

    private void requestConfirmation(Player player, String name, Economy economy) {
        var composer = MessageComposer.create()
                .text("<neutral>")
                .localeCode(
                        "company.create.create",
                        Replacement.create(
                                "amount",
                                String.format(
                                        "%s",
                                        economy.format(deps.configuration()
                                                .companySettings()
                                                .foundingPrice()))),
                        Replacement.unparsed("name", name))
                .newLine()
                .text("<click:run_command:/company create confirm><add>[")
                .localeCode("words.confirm")
                .text("]</click>")
                .space()
                .text("<click:run_command:/company create deny><remove>[")
                .localeCode("words.deny")
                .text("]</click>");
        messages().sendMessage(player, composer);
        registrations.request(player.getUniqueId(), name);
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
