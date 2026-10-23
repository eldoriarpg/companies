/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.order;

import org.bukkit.Material;

import java.util.List;
import java.util.Optional;

/**
 * An order with its lines.
 */
public interface IFullOrder extends ISimpleOrder {
    /**
     * The lines of the order in their order.
     *
     * @return lines
     */
    List<? extends IOrderContent> contents();

    /**
     * The price of the order, the sum of the line prices, without the fee.
     *
     * @return price
     */
    double price();

    /**
     * The number of requested items over all lines.
     *
     * @return amount
     */
    int amount();

    /**
     * The number of lines.
     *
     * @return line count
     */
    int materialsAmount();

    /**
     * The number of delivered items over all lines.
     *
     * @return delivered items
     */
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

    /**
     * Whether every line is fully delivered.
     *
     * @return true when nothing is missing
     */
    boolean isDone();
}
