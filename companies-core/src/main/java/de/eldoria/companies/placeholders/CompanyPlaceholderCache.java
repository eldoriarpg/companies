/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.placeholders;

import com.google.common.base.Ticker;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The placeholder values of companies, kept for the companies of cached players.
 * <p>
 * Values are reloaded by {@link #refreshAll()} and after local events; a value older than the refresh interval is
 * still answered while it reloads.
 */
public final class CompanyPlaceholderCache {
    private final StaleCache<Integer, CompanyView> cache;

    /**
     * @param loader      loads the values of a company; empty when the company does not exist
     * @param maximumSize most companies kept
     * @param refresh     age after which a value is reloaded
     */
    public CompanyPlaceholderCache(
            Function<Integer, CompletableFuture<Optional<CompanyView>>> loader, int maximumSize, Duration refresh) {
        this(loader, maximumSize, refresh, Ticker.systemTicker());
    }

    /**
     * @param loader      loads the values of a company; empty when the company does not exist
     * @param maximumSize most companies kept
     * @param refresh     age after which a value is reloaded
     * @param ticker      source of time
     */
    public CompanyPlaceholderCache(
            Function<Integer, CompletableFuture<Optional<CompanyView>>> loader,
            int maximumSize,
            Duration refresh,
            Ticker ticker) {
        this.cache = new StaleCache<>(loader, maximumSize, refresh, ticker);
    }

    /**
     * The values of a company.
     *
     * @param company id of the company
     * @return the values, or empty while they are loaded for the first time or when the company does not exist
     */
    public Optional<CompanyView> get(int company) {
        return cache.get(company);
    }

    /**
     * Reloads a company after a change, if it is cached.
     *
     * @param company id of the company
     * @return completes when the reload is done
     */
    public CompletableFuture<Void> invalidate(int company) {
        if (!cache.contains(company)) return CompletableFuture.completedFuture(null);
        return cache.reload(company);
    }

    /**
     * Drops a company that no longer exists.
     *
     * @param company id of the company
     */
    public void remove(int company) {
        cache.remove(company);
    }

    /**
     * Reloads every cached company.
     *
     * @return completes when every reload is done
     */
    public CompletableFuture<Void> refreshAll() {
        return CompletableFuture.allOf(cache.keys().stream().map(cache::reload).toArray(CompletableFuture[]::new));
    }
}
