/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.eldoria.companies.services.notifications.NotificationCategory;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Provides the suggestions of the inbox commands.
 */
public final class NotificationProviders {
    private NotificationProviders() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @return the mutable notification categories starting with what is typed
     */
    @NotificationCategories
    public static CompletableFuture<Suggestions> categories(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CompanyProviders.matching(categoryNames(), builder.getRemaining()).forEach(builder::suggest);
        return builder.buildFuture();
    }

    /**
     * @return the names of the mutable categories in lower case
     */
    static List<String> categoryNames() {
        return NotificationCategory.mutables().stream()
                .map(category -> category.name().toLowerCase(Locale.ROOT))
                .toList();
    }
}
