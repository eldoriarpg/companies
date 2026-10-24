/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.eldoria.companies.components.ranking.TimeRange;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Provides the suggestions of the company ranking.
 */
public final class RankingProviders {
    private RankingProviders() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @return the ids of the time ranges starting with what is typed
     */
    @TimeRanges
    public static CompletableFuture<Suggestions> timeRanges(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CompanyProviders.matching(rangeIds(), builder.getRemaining()).forEach(builder::suggest);
        return builder.buildFuture();
    }

    /**
     * @return the ids of the time ranges, all time first
     */
    public static List<String> rangeIds() {
        return Arrays.stream(TimeRange.values()).map(TimeRange::id).toList();
    }
}
