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
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.menus.ConfirmFlow;
import de.eldoria.companies.menus.MenuService;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.services.ActionResult;
import de.eldoria.companies.services.CompanyService;
import de.eldoria.companies.services.CompanyService.Payment;
import de.eldoria.companies.services.CompanyService.RenameQuote;
import de.eldoria.companies.util.Money;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.messages.Replacement;
import net.milkbowl.vault.economy.Economy;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.logging.Level;

/**
 * {@code /company rename <name>}: renames the company of its owner for the rename price after the owner confirmed it.
 * <p>
 * When the server allows it and the treasury covers the price, the confirmation offers to pay from the treasury
 * instead; {@code /company rename confirm treasury} does the same when typed. A rename paid from the treasury whose name
 * was taken meanwhile gives the price back to the treasury. The checks and the rename are made by
 * {@link CompanyService}.
 */
@Subcommand("rename")
@PlayerOnly
public final class Rename {
    public static final int MAX_NAME_LENGTH = CompanyService.MAX_NAME_LENGTH;
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

        deps.services()
                .companies()
                .renamable(player.getUniqueId(), name)
                .thenAcceptAsync(
                        result -> {
                            switch (result) {
                                case ActionResult.Refused<RenameQuote> refused ->
                                    refused.failure().sendTo(player, messages());
                                case ActionResult.Done<RenameQuote> done ->
                                    requestConfirmation(menus, player, name, economy, done.value());
                            }
                        },
                        Threading.mainThread(deps.plugin()))
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
        rename(player, name, Payment.PLAYER);
    }

    private void renameFromTreasury(Player player, String name) {
        rename(player, name, Payment.TREASURY);
    }

    private void rename(Player player, String name, Payment payment) {
        deps.services()
                .companies()
                .rename(player.getUniqueId(), name, payment)
                .thenAcceptAsync(
                        result -> {
                            switch (result) {
                                case ActionResult.Refused<CompanyProfile> refused ->
                                    refused.failure().sendTo(player, messages());
                                case ActionResult.Done<CompanyProfile> done ->
                                    messages().sendMessage(player, "company.rename.changed");
                            }
                        },
                        Threading.mainThread(deps.plugin()))
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not rename a company", err);
                    return null;
                });
    }

    private void requestConfirmation(
            MenuService menus, Player player, String name, Economy economy, RenameQuote quote) {
        var price = quote.price();
        var fromTreasury = quote.treasuryBalance().filter(balance -> balance.compareTo(BigDecimal.valueOf(price)) >= 0);
        if (!economy.has(player, price) && fromTreasury.isEmpty()) {
            messages()
                    .sendErrorActionBar(
                            player,
                            "error.insufficientCurrency",
                            Replacement.create("currency", Money.currencyName(economy)),
                            Replacement.create("amount", price));
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

    private MessageSender messages() {
        return deps.messages();
    }
}
