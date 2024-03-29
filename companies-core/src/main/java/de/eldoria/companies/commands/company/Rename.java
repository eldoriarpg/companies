/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.eldoutilities.commands.Completion;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.Replacement;
import de.eldoria.eldoutilities.scheduling.DelayedActions;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public class Rename extends AdvancedCommand implements IPlayerTabExecutor {
    public static final int MAX_NAME_LENGTH = 32;
    private final Map<UUID, String> confirm = new HashMap<>();
    private final Configuration configuration;
    private final Economy economy;
    private final ACompanyData companyData;
    private final DelayedActions delayedActions;

    public Rename(Plugin plugin, Configuration configuration, Economy economy, ACompanyData companyData) {
        super(plugin, CommandMeta.builder("rename")
                .addArgument("name", true)
                .build());
        this.configuration = configuration;
        this.economy = economy;
        this.companyData = companyData;
        delayedActions = DelayedActions.start(plugin);
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        if ("confirm".equalsIgnoreCase(arguments.asString(0))) {
            confirm(player);
            return;
        }

        CommandAssertions.invalidLength(arguments.join(), MAX_NAME_LENGTH);

        companyData.retrievePlayerCompanyProfile(player)
                   .asFuture()
                   .exceptionally(err -> {
                       plugin().getLogger()
                               .log(Level.SEVERE, "Something went wrong", err);
                       return Optional.empty();
                   })
                   .thenAcceptAsync(optProfile -> {
                       if (optProfile.isEmpty()) {
                           messageSender().sendErrorActionBar(player, "error.noMember");
                           return;
                       }
                       var profile = optProfile.get();
                       if (!profile.member(player)
                                   .get()
                                   .hasPermissions(CompanyPermission.OWNER)) {
                           messageSender().sendErrorActionBar(player, "error.noOwner");
                           return;
                       }

                       var name = arguments.join();
                       var company = companyData.retrieveCompanyByName(name)
                                                .join();
                       if (company.isPresent()) {
                           messageSender().sendErrorActionBar(player, "error.companyNameUsed");
                           return;
                       }

                       if (!economy.has(player, configuration.companySettings()
                                                             .renamePrice())) {
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

                       confirm.put(player.getUniqueId(), name);
                       var composer = MessageComposer.create()
                                                     .text("<neutral>")
                                                     .localeCode("company.rename.confirm",
                                                             Replacement.create("amount", String.format("<heading>%s<neutral>",
                                                                     economy.format(configuration.companySettings()
                                                                                                 .foundingPrice()))),
                                                             Replacement.create("name", String.format("<heading>%s<neutral>", name)))
                                                     .newLine()
                                                     .text("<%s><click:run_command:/company rename confirm><add>[")
                                                     .localeCode("words.confirm")
                                                     .text("]</click>");
                       messageSender().sendMessage(player, composer);
                       delayedActions.schedule(() -> expireConfirm(player.getUniqueId()), 30 * 20);
                   })
                   .exceptionally(err -> {
                       plugin().getLogger()
                               .log(Level.SEVERE, "Something went wrong", err);
                       return null;
                   })
        ;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return Completion.completeFreeInput(arguments.join(), MAX_NAME_LENGTH, localizer().localize("words.name"));
    }

    private void confirm(@NotNull Player player) {
        if (!confirm.containsKey(player.getUniqueId())) {
            messageSender().sendErrorActionBar(player, "error.noConfirm");
            return;
        }
        var name = confirm.get(player.getUniqueId());
        companyData.retrievePlayerCompanyProfile(player)
                   .asFuture()
                   .exceptionally(err -> {
                       plugin().getLogger()
                               .log(Level.SEVERE, "Something went wrong", err);
                       return Optional.empty();
                   })
                   .thenAccept(optProfile -> {
                       if (optProfile.isEmpty()) {
                           messageSender().sendErrorActionBar(player, "error.noMember");
                           return;
                       }
                       var profile = optProfile.get();
                       if (!profile.member(player)
                                   .get()
                                   .hasPermissions(CompanyPermission.OWNER)) {
                           messageSender().sendErrorActionBar(player, "error.noOwner");
                           return;
                       }

                       var company = companyData.retrieveCompanyByName(name)
                                                .join();
                       if (company.isPresent()) {
                           messageSender().sendErrorActionBar(player, "error.companyNameUsed");
                           return;
                       }

                       var response = economy.withdrawPlayer(player, configuration.companySettings()
                                                                                  .renamePrice());
                       if (response.type != EconomyResponse.ResponseType.SUCCESS) {
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

                       companyData.updateCompanyName(profile, name);
                       messageSender().sendMessage(player, "company.rename.changed");
                   })
                   .exceptionally(err -> {
                       plugin().getLogger()
                               .log(Level.SEVERE, "Something went wrong", err);
                       return null;
                   })
        ;
    }

    private void expireConfirm(UUID uuid) {
        if (confirm.remove(uuid) == null) return;

        var player = plugin().getServer()
                             .getOfflinePlayer(uuid);
        if (!player.isOnline()) return;

        messageSender().sendMessage(player.getPlayer(), "company.rename.expired");
    }
}
