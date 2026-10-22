/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.util.Confirmations;
import de.eldoria.companies.util.Permission;
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
import java.util.logging.Level;

public class Create extends AdvancedCommand implements IPlayerTabExecutor {
    public static final int MAX_NAME_LENGTH = 32;
    private final ACompanyData companyData;
    private final Configuration configuration;
    private final Economy economy;
    private final Confirmations<String> registrations = new Confirmations<>();

    public Create(Plugin plugin, ACompanyData companyData, Economy economy, Configuration configuration) {
        super(plugin, CommandMeta.builder("create")
                .addArgument("words.name", true)
                .withPermission(Permission.Company.CREATE)
                .build());
        this.companyData = companyData;
        this.economy = economy;
        this.configuration = configuration;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        if ("confirm".equalsIgnoreCase(arguments.asString(0))) {
            createCompany(player);
            return;
        }
        if ("deny".equalsIgnoreCase(arguments.asString(0))) {
            registrations.confirm(player.getUniqueId());
            messageSender().sendMessage(player, "words.canceled");
            return;
        }

        var name = arguments.join();
        CommandAssertions.invalidLength(name, MAX_NAME_LENGTH);

        companyData.retrievePlayerCompany(player)
                   .whenComplete(current -> {
                       if (current.isPresent()) {
                           messageSender().sendErrorActionBar(player, "error.alreadyMember");
                           return;
                       }
                       companyData.retrieveCompanyByName(name)
                                  .whenComplete(company -> {
                                      if (company.isPresent()) {
                                          messageSender().sendErrorActionBar(player, "error.companyNameUsed");
                                          return;
                                      }
                                      requestConfirmation(player, name);
                                  });
                   });
    }

    private void requestConfirmation(Player player, String name) {
        var composer = MessageComposer.create()
                                      .text("<neutral>")
                                      .localeCode("company.create.create",
                                              Replacement.create("amount", String.format("%s",
                                                      economy.format(configuration.companySettings()
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
        messageSender().sendMessage(player, composer);
        registrations.request(player.getUniqueId(), name);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return Completion.completeFreeInput(arguments.join(), 32, localizer().localize("words.name"));
    }

    private void createCompany(Player player) {
        var optName = registrations.confirm(player.getUniqueId());
        if (optName.isEmpty()) {
            messageSender().sendErrorActionBar(player, "error.confirmationExpired");
            return;
        }
        var name = optName.get();

        companyData.retrievePlayerCompany(player)
                   .whenComplete(current -> {
                       if (current.isPresent()) {
                           messageSender().sendErrorActionBar(player, "error.alreadyMember");
                           return;
                       }
                       companyData.retrieveCompanyByName(name)
                                  .whenComplete(company -> {
                                      if (company.isPresent()) {
                                          messageSender().sendErrorActionBar(player, "error.companyNameUsed");
                                          return;
                                      }
                                      foundCompany(player, name);
                                  });
                   });
    }

    private void foundCompany(Player player, String name) {
        var price = configuration.companySettings()
                                 .foundingPrice();
        if (!economy.has(player, price) || economy.withdrawPlayer(player, price).type != EconomyResponse.ResponseType.SUCCESS) {
            var fallbackCurr = economy.currencyNameSingular()
                                      .isBlank() ? MessageComposer.escape("words.money") : economy.currencyNameSingular();
            var curr = economy.currencyNamePlural()
                              .isBlank() ? fallbackCurr : economy.currencyNamePlural();
            messageSender().sendErrorActionBar(player, "error.insufficientCurrency",
                    Replacement.create("currency", curr),
                    Replacement.create("amount", price));
            return;
        }
        companyData.submitCompanyCreation(name)
                   .asFuture()
                   .exceptionally(err -> {
                       plugin().getLogger()
                               .log(Level.SEVERE, "Something went wrong", err);
                       return -1;
                   })
                   .thenApply(id -> {
                       if (id == -1) return false;
                       companyData.submitMemberUpdate(CompanyMember.forCompanyId(id, player)
                                                                   .addPermission(CompanyPermission.OWNER))
                                  .join();
                       return true;
                   })
                   .thenAcceptAsync(created -> {
                       if (!created) {
                           economy.depositPlayer(player, price);
                           messageSender().sendErrorActionBar(player, "error.saveFailed");
                           return;
                       }
                       messageSender().sendMessage(player, "company.create.created");
                   }, Threading.mainThread(plugin()));
    }
}
