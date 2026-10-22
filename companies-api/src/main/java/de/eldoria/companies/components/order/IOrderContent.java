/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.order;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * One line of an order: an item and the amount and total price requested for it.
 * <p>
 * A material line accepts every item of its material. An exact line requests the data of its template as well.
 */
public interface IOrderContent {
    /**
     * Position of the line in its order, starting at 0.
     *
     * @return line number
     */
    int line();

    /**
     * Whether the line requests the data of its template and not only its material.
     *
     * @return true for an exact line
     */
    boolean exact();

    /**
     * The price of one item of this line.
     *
     * @return the line price divided by the amount, or 0 when the amount is 0
     */
    double unitPrice();

    /**
     * The items delivered to this line, as they were delivered.
     *
     * @return delivered stacks
     */
    List<? extends IDeliveredStack> deliveries();

    int amount();

    void amount(int amount);

    List<? extends IContentPart> parts();

    double price();

    /**
     * The requested item with an amount of one.
     *
     * @return template of the line
     */
    ItemStack stack();

    int delivered();

    double percent();

    String materialIdentifier();

    String translatedMaterialString();

    Material material();

    int missing();
}
