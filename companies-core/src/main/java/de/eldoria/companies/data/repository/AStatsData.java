/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.components.ranking.TopOrder;
import de.eldoria.companies.data.wrapper.company.CompanyRank;
import de.eldoria.companies.data.wrapper.stats.MemberStats;
import de.eldoria.companies.data.wrapper.stats.PayoutRecord;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * The payout ledger, the company ranking and the member statistics.
 * <p>
 * All time rankings read the company statistics view: completed orders minus failed ones, the price of completed
 * orders and their items. Ranked time ranges read the payout ledger instead: orders, gross money and items of the
 * payouts in the window, and only companies with a payout in the window are ranked; failed orders count all time
 * only. Members and level are always the current values. Times are bound from Java, never taken from the database
 * clock. All futures complete on the executor given to the constructor.
 */
public abstract class AStatsData {
    private final ExecutorService executor;

    protected AStatsData(ExecutorService executor) {
        this.executor = executor;
    }

    /**
     * Writes payout records to the ledger in one batch.
     *
     * @param payouts records to write
     * @return true when every record was written
     */
    public CompletableFuture<Boolean> submitPayouts(List<PayoutRecord> payouts) {
        if (payouts.isEmpty()) return CompletableFuture.completedFuture(true);
        return CompletableFuture.supplyAsync(() -> savePayouts(List.copyOf(payouts)), executor);
    }

    protected abstract boolean savePayouts(List<PayoutRecord> payouts);

    /**
     * What every worker contributed to a company, the most money earned first. Workers who left the company are
     * included.
     *
     * @param company id of the company
     * @param since   first moment to count, or null for all time
     * @return statistics per worker
     */
    public CompletableFuture<List<MemberStats>> retrieveMemberStats(int company, @Nullable LocalDateTime since) {
        return CompletableFuture.supplyAsync(() -> getMemberStats(company, since), executor);
    }

    protected abstract List<MemberStats> getMemberStats(int company, @Nullable LocalDateTime since);

    /**
     * One page of the company ranking.
     *
     * @param order ordering of the ranking
     * @param range time range of the ranked values
     * @param now   current time, the end of ranged windows
     * @param page  page, starting at 1
     * @param size  companies per page
     * @return ranked companies of the page, best first
     */
    public CompletableFuture<List<CompanyRank>> retrieveRanking(
            TopOrder order, TimeRange range, LocalDateTime now, int page, int size) {
        return CompletableFuture.supplyAsync(() -> getRanking(order, range, now, page, size), executor);
    }

    protected abstract List<CompanyRank> getRanking(
            TopOrder order, TimeRange range, LocalDateTime now, int page, int size);

    /**
     * The place of one company in the ranking.
     *
     * @param company id of the company
     * @param order   ordering of the ranking
     * @param range   time range of the ranked values
     * @param now     current time, the end of ranged windows
     * @return the company's rank, or empty when it is not ranked in the range
     */
    public CompletableFuture<Optional<CompanyRank>> retrieveRank(
            int company, TopOrder order, TimeRange range, LocalDateTime now) {
        return CompletableFuture.supplyAsync(() -> getRank(company, order, range, now), executor);
    }

    protected abstract Optional<CompanyRank> getRank(int company, TopOrder order, TimeRange range, LocalDateTime now);

    /**
     * The all time place of every company.
     *
     * @param order ordering of the ranking
     * @return place by company id
     */
    public CompletableFuture<Map<Integer, Integer>> retrieveRankIndex(TopOrder order) {
        return CompletableFuture.supplyAsync(() -> getRankIndex(order), executor);
    }

    protected abstract Map<Integer, Integer> getRankIndex(TopOrder order);
}
