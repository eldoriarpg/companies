/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import de.eldoria.companies.data.repository.ASeriesData;
import de.eldoria.companies.data.wrapper.series.OrderSeries;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Where the suggestions of repeating orders read their data from: the series of each player, cached like other
 * database data.
 * <p>
 * The plugin hands the sources in when it enables and takes them back when it disables.
 */
public final class SeriesSources {
    private static volatile Optional<SeriesSources> current = Optional.empty();

    private final SuggestionCache<UUID, List<OrderSeries>> series;

    /**
     * @param seriesData repeating order repository
     * @param executor   executor loading data from the database
     */
    public SeriesSources(ASeriesData seriesData, Executor executor) {
        this(new SuggestionCache<>(SuggestionSources.LIFETIME, executor, player -> seriesData
                .retrieveByOwner(player, false)
                .join()));
    }

    /**
     * @param series cache of the series of a player, by the player's id
     */
    public SeriesSources(SuggestionCache<UUID, List<OrderSeries>> series) {
        this.series = series;
    }

    /**
     * @param sources the sources the providers read from
     */
    public static void use(SeriesSources sources) {
        current = Optional.of(sources);
    }

    /**
     * Stops suggesting data of the plugin.
     */
    public static void clear() {
        current = Optional.empty();
    }

    /**
     * @return the sources, empty while the plugin is disabled
     */
    public static Optional<SeriesSources> current() {
        return current;
    }

    /**
     * Drops the cached series of a player, after they changed.
     *
     * @param player id of the player
     */
    public static void invalidate(UUID player) {
        current.ifPresent(sources -> sources.series().invalidate(player));
    }

    /**
     * @return the series of a player, by the player's id
     */
    public SuggestionCache<UUID, List<OrderSeries>> series() {
        return series;
    }
}
