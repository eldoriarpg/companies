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
import de.eldoria.companies.util.Permission;
import de.eldoria.eldoutilities.commands.Completion;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.Replacement;
import de.eldoria.eldoutilities.threading.futures.CompletableBukkitFuture;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class Create extends AdvancedCommand implements IPlayerTabExecutor {
    public static final int MAX_NAME_LENGTH = 32;
    private final ACompanyData companyData;
    private final Configuration configuration;
    private final Economy economy;
    private final Map<UUID, String> registrations = new HashMap<>();

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
            var name = registrations.remove(player.getUniqueId());
            if (name == null) {
                messageSender().sendErrorActionBar(player, "error.noDeny");
                return;
            }
            messageSender().sendMessage(player, "words.canceled");
            return;
        }

        var name = arguments.join();
        CommandAssertions.invalidLength(name, MAX_NAME_LENGTH);

        companyData.retrieveCompanyByName(name)
                   .whenComplete(company -> {
                       if (company.isPresent()) {
                           messageSender().sendErrorActionBar(player, "error.companyNameUsed");
                           return;
                       }

                       var composer = MessageComposer.create()
                                                     .text("<neutral>")
                                                     .localeCode("company.create.create",
                                                             Replacement.create("AMOUNT", String.format("<heading>%s<neutral>",
                                                                     economy.format(configuration.companySettings()
                                                                                                 .foundingPrice()))),
                                                             Replacement.create("NAME", String.format("<heading>%s<neutral>", name)))
                                                     .newLine()
                                                     .text("<click:run_command:/company create confirm><add>[")
                                                     .localeCode("words.confirm")
                                                     .text("]</click>")
                                                     .space()
                                                     .text("<click:run_command:/company create deny><remove>[")
                                                     .localeCode("words.deny")
                                                     .text("]</click>");
                       messageSender().sendMessage(player, composer.build());
                       registrations.put(player.getUniqueId(), name);
                   });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return Completion.completeFreeInput(arguments.join(), 32, localizer().localize("words.name"));
    }

    private void createCompany(Player player) {
        var name = registrations.remove(player.getUniqueId());

        if (name == null) {
            messageSender().sendErrorActionBar(player, "error.noConfirm");
            return;
        }

        companyData.retrieveCompanyByName(name)
                   .whenComplete(company -> {
                       if (company.isPresent()) {
                           messageSender().sendErrorActionBar(player, "error.companyNameUsed");
                           return;
                       }

                       CompletableBukkitFuture.supplyAsync(() -> {
                                                  if (!economy.has(player, configuration.companySettings()
                                                                                        .foundingPrice())) {
                                                      return false;
                                                  }
                                                  return economy.withdrawPlayer(player, configuration.companySettings()
                                                                                                     .foundingPrice()).type == EconomyResponse.ResponseType.SUCCESS;
                                              })
                                              .whenComplete(result -> {
                                                  if (!result) {
                                                      var fallbackCurr = economy.currencyNameSingular()
                                                                                .isBlank() ? MessageComposer.escape("words.money") : economy.currencyNameSingular();
                                                      var curr = economy.currencyNamePlural()
                                                                        .isBlank() ? fallbackCurr : economy.currencyNamePlural();
                                                      messageSender().sendErrorActionBar(player, "error.insufficientCurrency",
                                                              Replacement.create("currency", curr),
                                                              Replacement.create("amount", configuration.companySettings()
                                                                                                        .foundingPrice()));
                                                      return;
                                                  }
                                                  companyData.submitCompanyCreation(name)
                                                             .asFuture()
                                                             .exceptionally(err -> {
                                                                 plugin().getLogger()
                                                                         .log(Level.SEVERE, "Something went wrong", err);
                                                                 return -1;
                                                             })
                                                             .thenAccept(id -> {
                                                                 if (id == -1) return;
                                                                 companyData.submitMemberUpdate(CompanyMember.forCompanyId(id, player)
                                                                                                             .addPermission(CompanyPermission.OWNER));
                                                             });
                                                  messageSender().sendMessage(player, "company.create.created");
                                              });
                   });
    }
}
