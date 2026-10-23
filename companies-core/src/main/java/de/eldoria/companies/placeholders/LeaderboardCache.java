/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.placeholders;

import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.components.ranking.TopOrder;
import de.eldoria.companies.data.wrapper.company.CompanyRank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The company rankings for every ordering and time range, as the placeholders read them.
 * <p>
 * Each ranking keeps its first places for the leaderboard placeholders and the place of every ranked company for the
 * rank placeholders. Rankings are replaced as a whole by {@link #refresh()}; until the first refresh of a ranking it
 * reads as not loaded.
 */
public final class LeaderboardCache {
    private final Map<Board, Ranking> rankings = new ConcurrentHashMap<>();
    private final Loader loader;
    private final int size;

    /**
     * @param loader loads a complete ranking
     * @param size   places kept for the leaderboards
     */
    public LeaderboardCache(Loader loader, int size) {
        this.loader = loader;
        this.size = size;
    }

    /**
     * Reloads every ranking. A ranking that fails to load keeps its previous state.
     *
     * @return completes when every ranking is loaded
     */
    public CompletableFuture<Void> refresh() {
        List<CompletableFuture<Void>> loads = new ArrayList<>();
        for (var order : TopOrder.values()) {
            for (var range : TimeRange.values()) {
                var board = new Board(order, range);
                loads.add(loader.ranking(order, range)
                        .thenAccept(ranks -> rankings.put(board, Ranking.of(ranks, size)))
                        .exceptionally(error -> null));
            }
        }
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new));
    }

    /**
     * Whether a ranking was loaded at least once.
     *
     * @param order ordering
     * @param range time range
     * @return true when the ranking can be read
     */
    public boolean loaded(TopOrder order, TimeRange range) {
        return rankings.containsKey(new Board(order, range));
    }

    /**
     * The company at a place of a leaderboard.
     *
     * @param order ordering
     * @param range time range
     * @param place place, from one up to the leaderboard size
     * @return the company, or empty when the place is not taken, beyond the leaderboard or not loaded
     */
    public Optional<CompanyRank> at(TopOrder order, TimeRange range, int place) {
        var ranking = rankings.get(new Board(order, range));
        if (ranking == null || place < 1 || place > ranking.top().size()) return Optional.empty();
        return Optional.of(ranking.top().get(place - 1));
    }

    /**
     * The place of a company in a ranking.
     *
     * @param company id of the company
     * @param order   ordering
     * @param range   time range
     * @return the place, or empty when the company is not ranked or the ranking is not loaded
     */
    public OptionalInt place(int company, TopOrder order, TimeRange range) {
        var ranking = rankings.get(new Board(order, range));
        if (ranking == null) return OptionalInt.empty();
        var place = ranking.places().get(company);
        return place == null ? OptionalInt.empty() : OptionalInt.of(place);
    }

    /**
     * Loads a complete ranking.
     */
    @FunctionalInterface
    public interface Loader {
        /**
         * @param order ordering
         * @param range time range
         * @return every ranked company, best first
         */
        CompletableFuture<List<CompanyRank>> ranking(TopOrder order, TimeRange range);
    }

    private record Board(TopOrder order, TimeRange range) {}

    private record Ranking(List<CompanyRank> top, Map<Integer, Integer> places) {
        static Ranking of(List<CompanyRank> ranks, int size) {
            Map<Integer, Integer> places = new HashMap<>();
            for (var rank : ranks) places.put(rank.id(), rank.rank());
            return new Ranking(List.copyOf(ranks.subList(0, Math.min(size, ranks.size()))), Map.copyOf(places));
        }
    }
}
