/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.components.company.ICompanyRank;
import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.components.ranking.TopOrder;
import de.eldoria.companies.data.repository.AStatsData;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Reads the ranking from the statistics repository, with the same windows as {@code /company top}.
 */
final class RankingApiImpl implements RankingApi {
    private final AStatsData stats;
    private final Supplier<LocalDateTime> clock;
    private final Executor executor;

    RankingApiImpl(AStatsData stats, Supplier<LocalDateTime> clock, Executor executor) {
        this.stats = stats;
        this.clock = clock;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<List<? extends ICompanyRank>> page(TopOrder order, TimeRange range, int page, int size) {
        if (page < 1) throw new IllegalArgumentException("The page must be at least 1, was " + page);
        if (size < 1) throw new IllegalArgumentException("The size must be at least 1, was " + size);
        return stats.retrieveRanking(order, range, clock.get(), page, size)
                .<List<? extends ICompanyRank>>thenApply(Function.identity())
                .thenApplyAsync(Function.identity(), executor);
    }

    @Override
    public CompletableFuture<Optional<? extends ICompanyRank>> rankOf(int company, TopOrder order, TimeRange range) {
        return stats.retrieveRank(company, order, range, clock.get())
                .<Optional<? extends ICompanyRank>>thenApply(Function.identity())
                .thenApplyAsync(Function.identity(), executor);
    }
}
