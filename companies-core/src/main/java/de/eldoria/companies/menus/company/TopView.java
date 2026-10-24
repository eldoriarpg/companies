/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.components.ranking.TopOrder;
import de.eldoria.companies.data.wrapper.company.CompanyRank;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * One page of the company ranking.
 *
 * @param ordering what the companies are ranked by
 * @param range    time range of the ranked values
 * @param page     number of the page, from one
 * @param ranks    the companies on this page, best first
 * @param hasNext  whether another page follows
 * @param own      the viewer's company and its place, or empty when the viewer has no company
 */
public record TopView(
        TopOrder ordering,
        TimeRange range,
        int page,
        List<CompanyRank> ranks,
        boolean hasNext,
        Optional<OwnCompany> own) {
    /**
     * A page as the ranking query returned it. A full page may be followed by another one.
     *
     * @param ordering what the companies are ranked by
     * @param range    time range of the ranked values
     * @param page     number of the page, from one
     * @param ranks    the companies on this page
     * @param pageSize companies on a full page
     * @param own      the viewer's company and its place, or empty when the viewer has no company
     * @return the view
     */
    public static TopView of(
            TopOrder ordering,
            TimeRange range,
            int page,
            List<CompanyRank> ranks,
            int pageSize,
            Optional<OwnCompany> own) {
        return new TopView(ordering, range, page, List.copyOf(ranks), ranks.size() >= pageSize, own);
    }

    /**
     * @return the paging controls of this page
     */
    Paging paging() {
        return Paging.open(page, hasNext);
    }

    /**
     * The viewer's company below the ranking.
     *
     * @param id    id of the company
     * @param name  name of the company
     * @param tag   tag of the company, or empty without one
     * @param color profile colour of the company, or empty without one
     * @param rank  place in the shown ordering and range, or empty when the company is not ranked in it
     */
    public record OwnCompany(int id, String name, Optional<String> tag, Optional<String> color, OptionalInt rank) {
        /**
         * The viewer's company at its place.
         *
         * @param company the viewer's company
         * @param rank    its place in the shown ordering and range, or empty when it is not ranked
         * @return the own company line
         */
        public static OwnCompany of(SimpleCompany company, Optional<CompanyRank> rank) {
            return new OwnCompany(
                    company.id(),
                    company.name(),
                    company.tag(),
                    company.color(),
                    rank.map(value -> OptionalInt.of(value.rank())).orElseGet(OptionalInt::empty));
        }
    }
}
