/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.order;

import org.bukkit.Material;

import java.util.List;
import java.util.Optional;

public interface IFullOrder extends ISimpleOrder {
    List<? extends IOrderContent> contents();

    double price();

    int amount();

    int materialsAmount();

    int delivered();

    /**
     * The delivered share of the ordered items.
     *
     * @return a value from 0 to 1, or 0 when nothing is ordered
     */
    double progress();

    /**
     * The line with the given number.
     *
     * @param line line number, starting at 0
     * @return the line, or empty when the order has no such line
     */
    Optional<? extends IOrderContent> line(int line);

    /**
     * The first material line of a material.
     *
     * @param material material of the line
     * @return the line, or empty when no material line requests the material
     * @deprecated an order may hold several lines of the same material; use {@link #line(int)}
     */
    @Deprecated
    Optional<? extends IOrderContent> content(Material material);

    boolean isDone();
}
