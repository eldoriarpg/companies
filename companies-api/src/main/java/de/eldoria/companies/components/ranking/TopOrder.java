/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.ranking;

import net.kyori.adventure.translation.Translatable;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * What the company ranking is sorted by. Every ordering is descending; equal companies are ordered by their id.
 */
public enum TopOrder implements Translatable {
    /**
     * Completed orders. All time, the failed orders of a company are subtracted.
     */
    ORDERS,
    /**
     * Money earned with completed orders, before tax.
     */
    EARNED,
    /**
     * Current members.
     */
    MEMBER,
    /**
     * Items delivered to completed orders.
     */
    ITEMS_DELIVERED,
    /**
     * Current level; companies on the same level are ordered by the money they earned in the time range.
     */
    LEVEL;

    @Override
    public @NotNull String translationKey() {
        return "enums.topOrder." + name().toLowerCase(Locale.ROOT);
    }
}
