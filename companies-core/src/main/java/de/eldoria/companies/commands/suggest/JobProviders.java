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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Suggestions of the job commands.
 */
public final class JobProviders {
    private static volatile List<String> surfaces;

    private JobProviders() {
        throw new UnsupportedOperationException("This is a utility class.");
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
