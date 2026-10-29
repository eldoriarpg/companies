/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.placeholders;

import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferState;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The event offers that run, goals and contests, as the placeholders read them: the soonest end first. Replaced as a
 * whole by {@link #refresh()}; until the first refresh it reads as not loaded.
 */
public final class OfferPlaceholderCache {
    private final Supplier<CompletableFuture<List<EventOffer>>> loader;
    private volatile List<EventOffer> active;

    /**
     * @param loader loads the offers that have not closed, the soonest end first
     */
    public OfferPlaceholderCache(Supplier<CompletableFuture<List<EventOffer>>> loader) {
        this.loader = loader;
    }

    /**
     * Reloads the offers. A failed load keeps the offers loaded before.
     *
     * @return completes when they are loaded
     */
    public CompletableFuture<Void> refresh() {
        return loader.get()
                .thenAccept(found -> active = found.stream()
                        .filter(offer -> offer.state() == OfferState.ACTIVE)
                        .toList())
                .exceptionally(error -> null);
    }

    /**
     * @return whether the offers were loaded at least once
     */
    public boolean loaded() {
        return active != null;
    }

    /**
     * @return the number of offers that run
     */
    public int count() {
        var current = active;
        return current == null ? 0 : current.size();
    }

    /**
     * @param place the place in the list, from one
     * @return the offer at the place, the soonest end first; empty beyond the list
     */
    public Optional<EventOffer> at(int place) {
        var current = active;
        if (current == null || place < 1 || place > current.size()) return Optional.empty();
        return Optional.of(current.get(place - 1));
    }
}
