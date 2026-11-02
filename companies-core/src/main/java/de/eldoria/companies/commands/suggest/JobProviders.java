/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.eldoria.companies.jobs.TerraformCheck;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Suggestions of the job commands.
 */
public final class JobProviders {
    private static volatile List<String> surfaces;
    private static volatile Optional<SuggestionCache<UUID, List<String>>> areas = Optional.empty();

    private JobProviders() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @param names    reads the names of a player's stored areas, may block
     * @param executor executor loading them
     */
    public static void useAreas(Function<UUID, List<String>> names, Executor executor) {
        areas = Optional.of(new SuggestionCache<>(SuggestionSources.LIFETIME, executor, names));
    }

    /**
     * Stops suggesting stored areas.
     */
    public static void clearAreas() {
        areas = Optional.empty();
    }

    /**
     * Drops the cached area names of a player, after they changed.
     *
     * @param player id of the player
     */
    public static void areasChanged(UUID player) {
        areas.ifPresent(cache -> cache.invalidate(player));
    }

    /**
     * @return the names of the sender's stored areas, starting with what is typed
     */
    @AreaNames
    public static CompletableFuture<Suggestions> areaNames(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        if (!(context.getSource().getSender() instanceof Player player)) return builder.buildFuture();
        return areas.map(cache ->
                        cache.suggest(player.getUniqueId(), builder, (names, target) -> CompanyProviders.matching(
                                        names, target.getRemaining())
                                .forEach(target::suggest)))
                .orElseGet(builder::buildFuture);
    }

    /**
     * @return the blocks a terraform job may ask for as its surface, starting with what is typed
     */
    @SurfaceBlocks
    public static CompletableFuture<Suggestions> surfaceBlocks(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CompanyProviders.matching(surfaces(), builder.getRemaining()).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static List<String> surfaces() {
        var known = surfaces;
        if (known != null) return known;
        known = Arrays.stream(Material.values())
                .filter(material -> !material.isLegacy())
                .map(material -> TerraformCheck.block(material.name()))
                .filter(Objects::nonNull)
                .map(material -> material.name().toLowerCase(Locale.ROOT))
                .toList();
        surfaces = known;
        return known;
    }
}
