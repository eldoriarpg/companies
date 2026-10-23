/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import java.time.Duration;

/**
 * How the PlaceholderAPI values are cached, the {@code placeholders} section of the config.
 * <p>
 * Values of online players, their companies and the leaderboards are reloaded every {@link #refresh()}, which is also
 * how long changes made on another node take to show. Offline players are kept up to {@link #offlineCacheSize()}.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class PlaceholderSettings {
    private int refreshSeconds = 300;
    private int offlineCacheSize = 500;
    private int leaderboardSize = 10;
    private String loadingText = "";

    public PlaceholderSettings() {}

    /**
     * Time between two reloads of a cached value. At least ten seconds.
     *
     * @return refresh interval
     */
    public Duration refresh() {
        return Duration.ofSeconds(Math.max(10, refreshSeconds));
    }

    /**
     * Offline players whose values are kept. At least one.
     *
     * @return maximum number of cached offline players
     */
    public int offlineCacheSize() {
        return Math.max(1, offlineCacheSize);
    }

    /**
     * Places of every leaderboard that can be read through placeholders. At least one.
     *
     * @return places per leaderboard
     */
    public int leaderboardSize() {
        return Math.max(1, leaderboardSize);
    }

    /**
     * Text shown while a value is loaded for the first time.
     *
     * @return loading text
     */
    public String loadingText() {
        return loadingText == null ? "" : loadingText;
    }
}
