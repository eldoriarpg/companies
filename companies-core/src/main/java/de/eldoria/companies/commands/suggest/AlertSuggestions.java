/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.eldoria.companies.data.repository.AAlertData;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.alert.OrderAlert;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Suggests the names of the order alerts of a player's company, cached like other database data.
 * <p>
 * The plugin hands the suggestions in when it enables and takes them back when it disables.
 */
public final class AlertSuggestions {
    private static volatile Optional<AlertSuggestions> current = Optional.empty();

    private final SuggestionCache<UUID, List<String>> names;

    /**
     * @param companyData company repository
     * @param alerts      alert repository
     * @param executor    executor loading data from the database
     */
    public AlertSuggestions(ACompanyData companyData, AAlertData alerts, Executor executor) {
        this(new SuggestionCache<>(
                SuggestionSources.LIFETIME, executor, player -> alertNames(companyData, alerts, player)));
    }

    /**
     * @param names cache of the alert names of a player's company, by the player's id
     */
    public AlertSuggestions(SuggestionCache<UUID, List<String>> names) {
        this.names = names;
    }

    /**
     * @param suggestions the suggestions the provider reads from
     */
    public static void use(AlertSuggestions suggestions) {
        current = Optional.of(suggestions);
    }

    /**
     * Stops suggesting data of the plugin.
     */
    public static void clear() {
        current = Optional.empty();
    }

    /**
     * Drops the cached alert names of a player's company, after the player changed them.
     *
     * @param player id of the player
     */
    public static void invalidate(UUID player) {
        current.ifPresent(suggestions -> suggestions.names.invalidate(player));
    }

    /**
     * @return names of the alerts of the sender's company, starting with what is typed
     */
    @CompanyAlertNames
    public static CompletableFuture<Suggestions> companyAlertNames(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        if (!(context.getSource().getSender() instanceof Player player)) return builder.buildFuture();
        return current.map(suggestions -> suggestions.names.suggest(
                        player.getUniqueId(), builder, (names, target) -> CompanyProviders.matching(
                                        names, target.getRemaining())
                                .forEach(target::suggest)))
                .orElseGet(builder::buildFuture);
    }

    private static List<String> alertNames(ACompanyData companyData, AAlertData alerts, UUID player) {
        var company = companyData
                .retrievePlayerCompany(Bukkit.getOfflinePlayer(player))
                .asFuture()
                .join();
        if (company == null || company.isEmpty()) return List.of();
        return alerts.retrieveByCompany(company.get().id()).join().stream()
                .map(OrderAlert::name)
                .toList();
    }
}
