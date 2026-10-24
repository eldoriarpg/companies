/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.order;

import org.bukkit.inventory.ItemStack;

/**
 * One line of an {@link IOrderDraft}.
 */
public interface IDraftLine {
    /**
     * The requested item with an amount of one.
     *
     * @return a copy of the template
     */
    ItemStack template();

    /**
     * Whether the line requests the data of its template and not only its material.
     *
     * @return true for an exact line
     */
    boolean exact();

    /**
     * The number of requested items.
     *
     * @return amount, at least 1
     */
    int amount();

    /**
     * Changes the number of requested items.
     *
     * @param amount new amount, at least 1; use {@link #remove()} to drop the line
     * @throws IllegalArgumentException when the amount is below 1
     * @throws IllegalStateException    when the line was removed
     */
    void amount(int amount);

    /**
     * The price of one item.
     *
     * @return unit price
     */
    double unitPrice();

    /**
     * Changes the price of one item.
     *
     * @param unitPrice new price, not negative
     * @throws IllegalArgumentException when the price is negative or not finite
     * @throws IllegalStateException    when the line was removed
     */
    void unitPrice(double unitPrice);

    /**
     * The price of the whole line.
     *
     * @return unit price times amount
     */
    double price();

    /**
     * Removes the line from its draft. Removing it again does nothing.
     */
    void remove();

    /**
     * Whether the line was removed from its draft.
     *
     * @return true once {@link #remove()} was called
     */
    boolean removed();
}
