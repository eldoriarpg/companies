/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards;

import de.eldoria.companies.orders.query.OrderFilter;
import org.jetbrains.annotations.Nullable;

/**
 * What a board shows: a preset of the configuration, a list of materials or every open order.
 *
 * @param preset name of the preset in lower case, or null when the board is not a preset
 * @param filter the orders shown
 * @param label  name of the board shown on signs, blank for every open order
 */
public record BoardTarget(@Nullable String preset, OrderFilter filter, String label) {
    /**
     * The board of every open order.
     */
    public static final BoardTarget ALL = new BoardTarget(null, OrderFilter.ALL, "");

    /**
     * The text a board is stored and resolved by again: the preset name, the materials separated by spaces, or an
     * empty text for every open order.
     *
     * @return the stored text of the board
     */
    public String spec() {
        if (preset != null) return preset;
        return String.join(" ", filter.materials());
    }
}
