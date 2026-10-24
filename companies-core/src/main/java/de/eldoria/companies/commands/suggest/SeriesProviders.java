/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.eldoria.companies.data.wrapper.series.OrderSeries;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Provides the suggestions of repeating orders.
 */
public final class SeriesProviders {
    private SeriesProviders() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @return ids of the sender's repeating orders starting with what is typed, with name and interval as tooltip
     */
    @OwnSeriesIds
    public static CompletableFuture<Suggestions> ownSeriesIds(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        if (!(context.getSource().getSender() instanceof Player player)) return builder.buildFuture();
        return SeriesSources.current()
                .map(sources -> sources.series().suggest(player.getUniqueId(), builder, (series, target) -> matching(
                                series, target.getRemaining())
                        .forEach(entry -> target.suggest(
                                entry.id(), MessageComponentSerializer.message().serialize(tooltip(entry))))))
                .orElseGet(builder::buildFuture);
    }

    /**
     * @param series series of the player
     * @param typed  what is typed so far
     * @return the series whose id starts with what is typed
     */
    public static List<OrderSeries> matching(List<OrderSeries> series, String typed) {
        return series.stream()
                .filter(entry -> String.valueOf(entry.id()).startsWith(typed))
                .toList();
    }

    private static Component tooltip(OrderSeries series) {
        return Component.text(series.name() + " (" + series.interval().name().toLowerCase(Locale.ROOT) + ")");
    }
}
