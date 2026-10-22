/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.util.Confirmations;
import de.eldoria.companies.util.Texts;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.commands.Completion;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.Replacement;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

public class Rename extends AdvancedCommand implements IPlayerTabExecutor {
    public static final int MAX_NAME_LENGTH = 32;
    private final Confirmations<String> confirm = new Confirmations<>();
    private final Configuration configuration;
    private final Economy economy;
    private final ACompanyData companyData;

    public Rename(Plugin plugin, Configuration configuration, Economy economy, ACompanyData companyData) {
        super(plugin, CommandMeta.builder("rename").addArgument("name", true).build());
        this.configuration = configuration;
        this.economy = economy;
        this.companyData = companyData;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments)
            throws CommandException {
        if ("confirm".equalsIgnoreCase(arguments.asString(0))) {
            confirm(player);
            return;
        }

        CommandAssertions.invalidLength(arguments.join(), MAX_NAME_LENGTH);

        companyData
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAcceptAsync(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendErrorActionBar(player, "error.noMember");
                        return;
                    }
                    var profile = optProfile.get();
                    if (!profile.member(player).get().hasPermissions(CompanyPermission.OWNER)) {
                        messageSender().sendErrorActionBar(player, "error.noOwner");
                        return;
                    }

                    var name = arguments.join();
                    var company = companyData.retrieveCompanyByName(name).join();
                    if (company.isPresent()) {
                        messageSender().sendErrorActionBar(player, "error.companyNameUsed");
                        return;
                    }

                    Threading.mainThread(plugin()).execute(() -> requestConfirmation(player, name));
                })
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return Completion.completeFreeInput(
                arguments.join(), MAX_NAME_LENGTH, localizer().localize("words.name"));
    }

    private void requestConfirmation(Player player, String name) {
        var price = configuration.companySettings().renamePrice();
        if (!economy.has(player, price)) {
            sendInsufficientCurrency(player, price);
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
        messageSender().sendMessage(player, composer);
    }

    private void sendInsufficientCurrency(Player player, double price) {
        var fallbackCurr = economy.currencyNameSingular().isBlank()
                ? MessageComposer.escape("words.money")
                : economy.currencyNameSingular();
        var curr = economy.currencyNamePlural().isBlank() ? fallbackCurr : economy.currencyNamePlural();
        messageSender()
                .sendErrorActionBar(
                        player,
                        "error.insufficientCurrency",
                        Replacement.create("currency", curr),
                        Replacement.create("amount", price));
    }

    private void confirm(@NotNull Player player) {
        var optName = confirm.confirm(player.getUniqueId());
        if (optName.isEmpty()) {
            messageSender().sendErrorActionBar(player, "error.confirmationExpired");
            return;
        }
        var name = optName.get();
        companyData
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendErrorActionBar(player, "error.noMember");
                        return;
                    }
                    var profile = optProfile.get();
                    if (!profile.member(player).get().hasPermissions(CompanyPermission.OWNER)) {
                        messageSender().sendErrorActionBar(player, "error.noOwner");
                        return;
                    }

                    var company = companyData.retrieveCompanyByName(name).join();
                    if (company.isPresent()) {
                        messageSender().sendErrorActionBar(player, "error.companyNameUsed");
                        return;
                    }

                    Threading.mainThread(plugin()).execute(() -> rename(player, profile, name));
                })
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    private void rename(Player player, SimpleCompany company, String name) {
        var price = configuration.companySettings().renamePrice();
        var response = economy.withdrawPlayer(player, price);
        if (response.type != EconomyResponse.ResponseType.SUCCESS) {
            sendInsufficientCurrency(player, price);
            return;
        }

        companyData
                .updateCompanyName(company, name)
                .asFuture()
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return false;
                })
                .thenAcceptAsync(
                        renamed -> {
                            if (!renamed) {
                                economy.depositPlayer(player, price);
                                messageSender().sendErrorActionBar(player, "error.companyNameUsed");
                                return;
                            }
                            messageSender().sendMessage(player, "company.rename.changed");
                        },
                        Threading.mainThread(plugin()));
    }
}
