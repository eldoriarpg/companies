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
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Provides the suggestions of the profile editor.
 */
public final class ProfileProviders {
    private ProfileProviders() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @return the palette colours starting with what is typed, in lower case
     */
    @PaletteColors
    public static CompletableFuture<Suggestions> paletteColors(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        var colors = SuggestionSources.current()
                .map(SuggestionSources::profileColors)
                .orElse(List.of());
        CompanyProviders.matching(paletteNames(colors), builder.getRemaining()).forEach(builder::suggest);
        return builder.buildFuture();
    }

    /**
     * @param colors the configured palette
     * @return the palette in lower case, as it is typed
     */
    public static List<String> paletteNames(List<String> colors) {
        return colors.stream()
                .map(color -> color.strip().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
