/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.eldoria.companies.commands.company.TopOrder;
import de.eldoria.companies.components.company.CompanyPermission;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Provides the suggestions about companies, their members and players.
 */
public final class CompanyProviders {
    private CompanyProviders() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @return member names of the sender's company starting with what is typed
     */
    @CompanyMembers
    public static CompletableFuture<Suggestions> members(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        if (!(context.getSource().getSender() instanceof Player player)) return builder.buildFuture();
        return SuggestionSources.current()
                .map(sources -> sources.memberNames().suggest(player.getUniqueId(), builder, CompanyProviders::suggest))
                .orElseGet(builder::buildFuture);
    }

    /**
     * @return company names starting with what is typed, as they are
     */
    @CompanyNames
    public static CompletableFuture<Suggestions> companyNames(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SuggestionSources.current()
                .map(sources -> sources.companyNames()
                        .suggest(SuggestionSources.allCompanies(), builder, CompanyProviders::suggest))
                .orElseGet(builder::buildFuture);
    }

    /**
     * @return company names starting with what is typed, quoted where a string argument needs it
     */
    @QuotedCompanyNames
    public static CompletableFuture<Suggestions> quotedCompanyNames(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SuggestionSources.current()
                .map(sources -> sources.companyNames()
                        .suggest(
                                SuggestionSources.allCompanies(),
                                builder,
                                (names, target) -> suggest(
                                        names.stream()
                                                .map(CompanyProviders::quoted)
                                                .toList(),
                                        target)))
                .orElseGet(builder::buildFuture);
    }

    /**
     * @return the permissions a member can be given, starting with what is typed
     */
    @CompanyPermissions
    public static CompletableFuture<Suggestions> permissions(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        suggest(permissionNames(), builder);
        return builder.buildFuture();
    }

    /**
     * @return the orderings of the ranking starting with what is typed
     */
    @TopOrderings
    public static CompletableFuture<Suggestions> orderings(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        suggest(
                Arrays.stream(TopOrder.values())
                        .map(order -> order.name().toLowerCase(Locale.ROOT))
                        .toList(),
                builder);
        return builder.buildFuture();
    }

    /**
     * @return names of online players starting with what is typed
     */
    @KnownPlayers
    public static CompletableFuture<Suggestions> players(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        suggest(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), builder);
        return builder.buildFuture();
    }

    /**
     * @return the permissions a member can be given, in lower case; the owner permission is passed on by transfer
     */
    public static List<String> permissionNames() {
        return Arrays.stream(CompanyPermission.values())
                .filter(permission -> permission != CompanyPermission.OWNER)
                .map(permission -> permission.name().toLowerCase(Locale.ROOT))
                .toList();
    }

    /**
     * @param values values to offer
     * @param typed  what is typed so far
     * @return the values starting with what is typed, ignoring case, in their order
     */
    public static List<String> matching(Collection<String> values, String typed) {
        var prefix = typed.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }

    /**
     * Quotes a name for a Brigadier string argument, unless it is a single unquoted word.
     *
     * @param name name to suggest
     * @return the name as it has to be typed
     */
    public static String quoted(String name) {
        var plain = !name.isEmpty() && name.chars().allMatch(c -> StringReader.isAllowedInUnquotedString((char) c));
        if (plain) return name;
        return '"' + name.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static void suggest(Collection<String> values, SuggestionsBuilder builder) {
        matching(values, builder.getRemaining()).forEach(builder::suggest);
    }
}
