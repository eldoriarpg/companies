/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.menus.company.MemberContributions;
import de.eldoria.companies.menus.company.MemberSorting;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * Suggestions of the member list.
 */
public final class MemberProviders {
    private MemberProviders() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @return the sortings of the member list starting with what is typed
     */
    @MemberSortings
    public static CompletableFuture<Suggestions> sortings(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        var ids = Arrays.stream(MemberSorting.values()).map(MemberSorting::id).toList();
        CompanyProviders.matching(ids, builder.getRemaining()).forEach(builder::suggest);
        return builder.buildFuture();
    }

    /**
     * @return the ranges of member contributions starting with what is typed
     */
    @StatRanges
    public static CompletableFuture<Suggestions> ranges(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        var ids = MemberContributions.RANGES.stream().map(TimeRange::id).toList();
        CompanyProviders.matching(ids, builder.getRemaining()).forEach(builder::suggest);
        return builder.buildFuture();
    }
}
