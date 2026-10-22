/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.components.company.ICompanyRank;
import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.components.ranking.TopOrder;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Reads the company ranking that {@code /company top} shows.
 * <p>
 * Every future completes off the main thread; see {@link CompaniesApi} for how to get back onto it.
 */
public interface RankingApi {
    /**
     * One page of the ranking.
     *
     * @param order what the ranking is sorted by
     * @param range time range of the ranked values; only companies with activity in a window are ranked in it
     * @param page  page, starting at 1
     * @param size  companies per page, at least 1
     * @return future of the ranked companies of the page, best first; empty past the last page
     * @throws IllegalArgumentException when the page or the size is below 1
     */
    CompletableFuture<List<? extends ICompanyRank>> page(TopOrder order, TimeRange range, int page, int size);

    /**
     * The place of one company in the ranking.
     *
     * @param company id of the company
     * @param order   what the ranking is sorted by
     * @param range   time range of the ranked values
     * @return future of the company's rank, empty when it is unknown or not ranked in the range
     */
    CompletableFuture<Optional<? extends ICompanyRank>> rankOf(int company, TopOrder order, TimeRange range);
}
