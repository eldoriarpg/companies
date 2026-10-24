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
 * Provides the suggestions of the NPC boards.
 */
public final class NpcProviders {
    private NpcProviders() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @return the loaded NPC hooks starting with what is typed
     */
    @NpcHookNames
    public static CompletableFuture<Suggestions> hooks(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        var hooks = SuggestionSources.current().map(SuggestionSources::npcHooks).orElse(List.of());
        CompanyProviders.matching(hooks, builder.getRemaining()).forEach(builder::suggest);
        return builder.buildFuture();
    }

    /**
     * @return the NPCs of the typed hook starting with what is typed
     */
    @NpcKeys
    public static CompletableFuture<Suggestions> npcs(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        var hook = hook(context);
        var npcs = SuggestionSources.current()
                .map(sources -> sources.npcKeys(hook))
                .orElse(List.of());
        CompanyProviders.matching(npcs, builder.getRemaining()).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static String hook(CommandContext<CommandSourceStack> context) {
        try {
            return context.getArgument("hook", String.class);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
