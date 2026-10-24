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
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Provides the suggestions of saved items.
 */
public final class SavedItemProviders {
    private SavedItemProviders() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @return names of the sender's saved items starting with what is typed, with the item as tooltip
     */
    @SavedItemNames
    public static CompletableFuture<Suggestions> savedItemNames(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        if (!(context.getSource().getSender() instanceof Player player)) return builder.buildFuture();
        return SavedItemSources.current()
                .map(sources -> sources.items().suggest(player.getUniqueId(), builder, (items, target) -> items.stream()
                        .filter(item -> item.name()
                                .toLowerCase(Locale.ROOT)
                                .startsWith(target.getRemaining().toLowerCase(Locale.ROOT)))
                        .forEach(item -> target.suggest(
                                item.name(),
                                MessageComponentSerializer.message()
                                        .serialize(Component.translatable(
                                                item.stack().translationKey()))))))
                .orElseGet(builder::buildFuture);
    }
}
