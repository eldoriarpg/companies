/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.eldoria.companies.commands.suggest.MembershipSources.PlayerMembership;
import de.eldoria.companies.components.company.JoinMode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Provides the suggestions of invites, applications and joining.
 */
public final class MembershipProviders {
    private MembershipProviders() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @return players the sender's company invited, starting with what is typed
     */
    @InvitedPlayers
    public static CompletableFuture<Suggestions> invitedPlayers(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return fromPlayer(context, builder, PlayerMembership::invitedPlayers);
    }

    /**
     * @return companies that invited the sender, starting with what is typed
     */
    @PendingInviteCompanies
    public static CompletableFuture<Suggestions> invitingCompanies(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return fromPlayer(context, builder, PlayerMembership::invitingCompanies);
    }

    /**
     * @return companies the sender applied to, starting with what is typed
     */
    @OwnApplicationCompanies
    public static CompletableFuture<Suggestions> appliedCompanies(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return fromPlayer(context, builder, PlayerMembership::appliedCompanies);
    }

    /**
     * @return players who applied to the sender's company, starting with what is typed
     */
    @ApplicantNames
    public static CompletableFuture<Suggestions> applicants(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return fromPlayer(context, builder, PlayerMembership::applicants);
    }

    /**
     * @return companies open to every player, starting with what is typed
     */
    @JoinableCompanyNames
    public static CompletableFuture<Suggestions> openCompanies(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return companies(JoinMode.OPEN, builder, Function.identity());
    }

    /**
     * @return companies accepting applications, starting with what is typed and quoted where needed
     */
    @ApplicableCompanyNames
    public static CompletableFuture<Suggestions> applyingCompanies(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return companies(JoinMode.APPLICATIONS, builder, CompanyProviders::quoted);
    }

    private static CompletableFuture<Suggestions> fromPlayer(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            Function<PlayerMembership, List<String>> values) {
        if (!(context.getSource().getSender() instanceof Player player)) return builder.buildFuture();
        return MembershipSources.current()
                .map(sources -> sources.players()
                        .suggest(
                                player.getUniqueId(),
                                builder,
                                (membership, target) -> suggest(values.apply(membership), target)))
                .orElseGet(builder::buildFuture);
    }

    private static CompletableFuture<Suggestions> companies(
            JoinMode mode, SuggestionsBuilder builder, Function<String, String> written) {
        return MembershipSources.current()
                .map(sources -> sources.companies()
                        .suggest(
                                mode,
                                builder,
                                (names, target) ->
                                        suggest(names.stream().map(written).toList(), target)))
                .orElseGet(builder::buildFuture);
    }

    private static void suggest(List<String> values, SuggestionsBuilder builder) {
        CompanyProviders.matching(values, builder.getRemaining()).forEach(builder::suggest);
    }
}
