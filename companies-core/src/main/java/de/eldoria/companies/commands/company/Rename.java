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
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.util.Confirmations;
import de.eldoria.companies.util.Texts;
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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.logging.Level;

/**
 * {@code /company rename <name>}: renames the company of its owner for the rename price after the owner confirmed it.
 */
@Subcommand("rename")
@PlayerOnly
public final class Rename {
    public static final int MAX_NAME_LENGTH = 32;
    private final Confirmations<String> confirm = new Confirmations<>();
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public Rename(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Asks the owner to confirm the new name and its price.
     *
     * @param sender sender of the command
     * @param name   new name of the company
     */
    @Executes
    public void rename(CommandSender sender, @StringArg(StringArgType.GREEDY) String name) {
        var player = Senders.player(sender);
        var economy = deps.services().economy();
        Checks.shorterThan(name, MAX_NAME_LENGTH);
        var companyData = deps.companyData();

        companyData
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAcceptAsync(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messages().sendErrorActionBar(player, "error.noMember");
                        return;
                    }
                    var profile = optProfile.get();
                    if (!profile.member(player).get().hasPermissions(CompanyPermission.OWNER)) {
                        messages().sendErrorActionBar(player, "error.noOwner");
                        return;
                    }

                    var company = companyData.retrieveCompanyByName(name).join();
                    if (company == null) {
                        messages().sendErrorActionBar(player, "error.saveFailed");
                        return;
                    }
                    if (company.isPresent()) {
                        messages().sendErrorActionBar(player, "error.companyNameUsed");
                        return;
                    }

                    Threading.mainThread(deps.plugin()).execute(() -> requestConfirmation(player, name, economy));
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    /**
     * Renames the company to the name the owner confirmed.
     *
     * @param sender sender of the command
     */
    @Executes("confirm")
    public void confirm(CommandSender sender) {
        var player = Senders.player(sender);
        var economy = deps.services().economy();
        var optName = confirm.confirm(player.getUniqueId());
        if (optName.isEmpty()) {
            messages().sendErrorActionBar(player, "error.confirmationExpired");
            return;
        }
        var name = optName.get();
        var companyData = deps.companyData();
        companyData
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messages().sendErrorActionBar(player, "error.noMember");
                        return;
                    }
                    var profile = optProfile.get();
                    if (!profile.member(player).get().hasPermissions(CompanyPermission.OWNER)) {
                        messages().sendErrorActionBar(player, "error.noOwner");
                        return;
                    }

                    var company = companyData.retrieveCompanyByName(name).join();
                    if (company == null) {
                        messages().sendErrorActionBar(player, "error.saveFailed");
                        return;
                    }
                    if (company.isPresent()) {
                        messages().sendErrorActionBar(player, "error.companyNameUsed");
                        return;
                    }

                    Threading.mainThread(deps.plugin()).execute(() -> rename(player, profile, name, economy));
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    private void requestConfirmation(Player player, String name, Economy economy) {
        var price = deps.configuration().companySettings().renamePrice();
        if (!economy.has(player, price)) {
            sendInsufficientCurrency(player, price, economy);
            return;
        }

        confirm.request(player.getUniqueId(), name);
        var composer = MessageComposer.create()
                .text("<neutral>")
                .localeCode(
                        "company.rename.confirm",
                        Replacement.create("amount", String.format("<heading>%s<neutral>", economy.format(price))),
                        Replacement.create("name", String.format("<heading>%s<neutral>", Texts.escape(name))))
                .newLine()
                .text("<click:run_command:/company rename confirm><add>[")
                .localeCode("words.confirm")
                .text("]</click>");
        messages().sendMessage(player, composer);
    }

    private void sendInsufficientCurrency(Player player, double price, Economy economy) {
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
    }

    private void rename(Player player, SimpleCompany company, String name, Economy economy) {
        var price = deps.configuration().companySettings().renamePrice();
        var response = economy.withdrawPlayer(player, price);
        if (response.type != EconomyResponse.ResponseType.SUCCESS) {
            sendInsufficientCurrency(player, price, economy);
            return;
        }

        deps.companyData()
                .updateCompanyName(company, name)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return false;
                })
                .thenAcceptAsync(
                        renamed -> {
                            if (!renamed) {
                                economy.depositPlayer(player, price);
                                messages().sendErrorActionBar(player, "error.companyNameUsed");
                                return;
                            }
                            messages().sendMessage(player, "company.rename.changed");
                        },
                        Threading.mainThread(deps.plugin()));
    }

    private MessageSender messages() {
        return deps.messages();
    }
}
