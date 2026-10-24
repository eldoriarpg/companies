/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Provides the suggestions of the order boards.
 */
public final class BoardProviders {
    private BoardProviders() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @return the preset names starting with what is typed
     */
    @BoardPresets
    public static CompletableFuture<Suggestions> presets(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        var presets =
                SuggestionSources.current().map(SuggestionSources::boardPresets).orElse(List.of());
        CompanyProviders.matching(presets, builder.getRemaining()).forEach(builder::suggest);
        return builder.buildFuture();
    }
}
