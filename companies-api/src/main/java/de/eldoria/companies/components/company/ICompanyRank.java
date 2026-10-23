/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.company;

import java.util.Optional;

/**
 * A company at its place in a ranking.
 * <p>
 * The values of {@link ICompanyStats} are those of the ranked time range: {@link #orderCount()},
 * {@link #price()} and {@link #deliveredItems()} cover only the range, {@link #memberCount()} is the current number of
 * members.
 */
public interface ICompanyRank extends ICompanyStats {
    /**
     * The place in the ranking.
     *
     * @return rank, starting at 1
     */
    int rank();

    /**
     * The current level of the company.
     *
     * @return level, starting at 1
     */
    int level();

    /**
     * The tag of the company, for display next to its name.
     *
     * @return upper case tag, or empty when none is set
     */
    Optional<String> tag();

    /**
     * The colour of the company's tag and name.
     *
     * @return name of a named text colour or a {@code #rrggbb} value, or empty when none is set
     */
    Optional<String> color();
}
