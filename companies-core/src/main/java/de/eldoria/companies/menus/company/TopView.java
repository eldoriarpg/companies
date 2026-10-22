/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.ranking.TopOrder;
import de.eldoria.companies.data.wrapper.company.CompanyRank;

import java.util.List;

/**
 * One page of the company ranking.
 *
 * @param ordering what the companies are ranked by
 * @param page     number of the page, from one
 * @param ranks    the companies on this page, best first
 * @param hasNext  whether another page follows
 */
public record TopView(TopOrder ordering, int page, List<CompanyRank> ranks, boolean hasNext) {
    /**
     * A page as the ranking query returned it. A full page may be followed by another one.
     *
     * @param ordering what the companies are ranked by
     * @param page     number of the page, from one
     * @param ranks    the companies on this page
     * @param pageSize companies on a full page
     * @return the view
     */
    public static TopView of(TopOrder ordering, int page, List<CompanyRank> ranks, int pageSize) {
        return new TopView(ordering, page, List.copyOf(ranks), ranks.size() >= pageSize);
    }

    /**
     * @return the paging controls of this page
     */
    Paging paging() {
        return Paging.open(page, hasNext);
    }
}
