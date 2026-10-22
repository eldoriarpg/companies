/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.company;

import de.eldoria.companies.components.company.ICompanyRank;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * A company at its place in a ranking, with the values of the ranked time range and its current level, tag and
 * colour for display.
 */
public class CompanyRank extends CompanyStats implements ICompanyRank {
    private final int rank;
    private final int level;

    @Nullable
    private final String tag;

    @Nullable
    private final String color;

    /**
     * @param rank        place in the ranking, starting at 1
     * @param id          id of the company
     * @param name        name of the company
     * @param founded     when the company was founded
     * @param memberCount current members
     * @param orderCount  completed orders in the range
     * @param price       money earned in the range, before tax
     * @param amount      items delivered in the range
     * @param level       current level
     * @param tag         tag, or null without one
     * @param color       profile colour, or null without one
     */
    public CompanyRank(
            int rank,
            int id,
            String name,
            LocalDateTime founded,
            int memberCount,
            int orderCount,
            double price,
            int amount,
            int level,
            @Nullable String tag,
            @Nullable String color) {
        super(id, name, founded, memberCount, orderCount, price, amount);
        this.rank = rank;
        this.level = level;
        this.tag = tag;
        this.color = color;
    }

    @Override
    public int rank() {
        return rank;
    }

    /**
     * @return current level of the company
     */
    public int level() {
        return level;
    }

    /**
     * @return tag of the company, or empty without one
     */
    public Optional<String> tag() {
        return Optional.ofNullable(tag);
    }

    /**
     * @return profile colour of the company, or empty without one
     */
    public Optional<String> color() {
        return Optional.ofNullable(color);
    }
}
