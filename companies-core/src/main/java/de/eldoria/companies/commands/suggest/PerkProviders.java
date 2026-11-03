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
import java.util.function.Supplier;

/**
 * Suggestions of the perk commands.
 */
public final class PerkProviders {
    private static volatile Supplier<List<String>> perks = List::of;

    private PerkProviders() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @param ids the ids of the perks companies can buy
     */
    public static void use(Supplier<List<String>> ids) {
        perks = ids;
    }

    /**
     * Stops suggesting perks.
     */
    public static void clear() {
        perks = List::of;
    }

    /**
     * @return the ids of the perks, starting with what is typed
     */
    @PerkIds
    public static CompletableFuture<Suggestions> perkIds(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CompanyProviders.matching(perks.get(), builder.getRemaining()).forEach(builder::suggest);
        return builder.buildFuture();
    }
}
