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
import de.eldoria.companies.components.company.TreasuryKind;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.menus.ConfirmFlow;
import de.eldoria.companies.menus.MenuService;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
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

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * {@code /company rename <name>}: renames the company of its owner for the rename price after the owner confirmed it.
 * <p>
 * When the server allows it and the treasury covers the price, the confirmation offers to pay from the treasury
 * instead; {@code /company rename confirm treasury} does the same when typed. A rename paid from the treasury whose name
 * was taken meanwhile gives the price back to the treasury.
 */
@Subcommand("rename")
@PlayerOnly
public final class Rename {
    public static final int MAX_NAME_LENGTH = 32;
    private final CommandDependencies deps;
    private final ConfirmFlow<String> renames;

    /**
     * @param deps what the commands need
     */
    public Rename(CommandDependencies deps) {
        this.deps = deps;
        this.renames = ConfirmFlow.create(deps.plugin(), this::renameConfirmed);
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
        var menus = deps.services().menus();
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

                    var treasuryBalance = treasuryBalance(profile);
                    Threading.mainThread(deps.plugin())
                            .execute(() -> requestConfirmation(menus, player, name, economy, treasuryBalance));
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
        deps.requireReady();
        renames.confirm(player);
    }

    /**
     * Renames the company to the name the owner confirmed and pays the price from the treasury.
     *
     * @param sender sender of the command
     */
    @Executes("confirm treasury")
    public void confirmTreasury(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        renames.confirmWith(player, this::renameFromTreasury);
    }

    private void renameConfirmed(Player player, String name) {
        var economy = deps.services().economy();
        whenStillValid(player, name, profile -> Threading.mainThread(deps.plugin())
                .execute(() -> rename(player, profile, name, economy)));
    }

    private void renameFromTreasury(Player player, String name) {
        whenStillValid(player, name, profile -> payFromTreasury(player, profile, name));
    }

    private void whenStillValid(Player player, String name, Consumer<CompanyProfile> action) {
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

                    action.accept(profile);
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    private void requestConfirmation(
            MenuService menus, Player player, String name, Economy economy, Optional<BigDecimal> treasuryBalance) {
        var price = deps.configuration().companySettings().renamePrice();
        var fromTreasury = treasuryBalance.filter(balance -> balance.compareTo(BigDecimal.valueOf(price)) >= 0);
        if (!economy.has(player, price) && fromTreasury.isEmpty()) {
            sendInsufficientCurrency(player, price, economy);
            return;
        }

        var question = MenuText.create()
                .locale(
                        "company.rename.confirm",
                        Values.money("amount", economy, price),
                        Values.text("company_name", name));
        if (fromTreasury.isEmpty()) {
            renames.ask(menus, player, name, question);
            return;
        }
        var treasuryLabel = MenuText.create()
                .locale(
                        "company.rename.payFromTreasury",
                        Values.money("bank_balance", economy, fromTreasury.get().doubleValue()));
        renames.ask(
                menus,
                player,
                name,
                question,
                MenuText.create().locale("company.rename.paySelf"),
                new ConfirmFlow.Alternative<>(treasuryLabel, this::renameFromTreasury));
    }

    /**
     * The balance of the company's treasury when the rename may be paid from it. Blocks.
     *
     * @param company company to rename
     * @return the balance, or empty when the rename cannot be paid from the treasury
     */
    private Optional<BigDecimal> treasuryBalance(CompanyProfile company) {
        var treasury = deps.services().treasury();
        var settings = treasury.settings();
        if (!settings.enabled() || !settings.payRename()) return Optional.empty();
        if (!(deps.configuration().companySettings().renamePrice() > 0)) return Optional.empty();
        return Optional.of(treasury.treasury(company.id()).join().balance());
    }

    private void payFromTreasury(Player player, CompanyProfile company, String name) {
        var treasury = deps.services().treasury();
        if (!treasury.settings().enabled() || !treasury.settings().payRename()) {
            messages().sendErrorActionBar(player, "company.bank.error.disabled");
            return;
        }
        var price = BigDecimal.valueOf(deps.configuration().companySettings().renamePrice());
        treasury.spend(company, player.getUniqueId(), price, TreasuryKind.RENAME, name, () -> deps.companyData()
                        .updateCompanyName(company, name)
                        .asFuture())
                .thenAcceptAsync(
                        result -> {
                            switch (result.outcome()) {
                                case DONE -> {
                                    deps.companyChanges()
                                            .renamed(company.id(), company.name(), player.getUniqueId(), false);
                                    messages().sendMessage(player, "company.rename.changed");
                                }
                                case PURCHASE_FAILED -> messages().sendErrorActionBar(player, "error.companyNameUsed");
                                case TREASURY_FUNDS ->
                                    messages().sendErrorActionBar(player, "company.bank.error.insufficient");
                                case DISABLED -> messages().sendErrorActionBar(player, "company.bank.error.disabled");
                                default -> messages().sendErrorActionBar(player, "error.saveFailed");
                            }
                        },
                        Threading.mainThread(deps.plugin()))
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not rename a company", err);
                    return null;
                });
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
                            deps.companyChanges().renamed(company.id(), company.name(), player.getUniqueId(), false);
                            messages().sendMessage(player, "company.rename.changed");
                        },
                        Threading.mainThread(deps.plugin()));
    }

    private MessageSender messages() {
        return deps.messages();
    }
}
