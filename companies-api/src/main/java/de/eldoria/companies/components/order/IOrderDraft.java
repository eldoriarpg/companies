/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.order;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * An order that is about to be created, as listeners of
 * {@link de.eldoria.companies.events.order.PreOrderCreateEvent} see and change it.
 * <p>
 * Values are checked when they are set. The limits of the server (lines and items per order, name length) and that
 * the order keeps at least one line are checked once all listeners ran; a draft that breaks them is not created.
 */
public interface IOrderDraft {
    /**
     * Creates an empty draft.
     *
     * @param name name of the order
     * @return a new draft without lines
     * @throws IllegalArgumentException when the name is blank
     */
    static IOrderDraft of(String name) {
        return new OrderDraft(name);
    }

    /**
     * The name of the order.
     *
     * @return name
     */
    String name();

    /**
     * Renames the order.
     *
     * @param name new name
     * @throws IllegalArgumentException when the name is blank
     */
    void name(String name);

    /**
     * The lines of the order in their order.
     * <p>
     * The list is a snapshot: removing a line while iterating over it is safe, and lines added later are not in it.
     *
     * @return unmodifiable list of the current lines
     */
    List<? extends IDraftLine> lines();

    /**
     * Adds a line that accepts every item of a material.
     *
     * @param material  requested material
     * @param amount    number of items, at least 1
     * @param unitPrice price of one item, not negative
     * @return the added line
     * @throws IllegalArgumentException when the material is not an item or a value is out of range
     */
    IDraftLine addLine(Material material, int amount, double unitPrice);

    /**
     * Adds a line that accepts only items matching a template.
     *
     * @param template  requested item; its amount is ignored and the stack is copied
     * @param amount    number of items, at least 1
     * @param unitPrice price of one item, not negative
     * @return the added line
     * @throws IllegalArgumentException when the template is not an item or a value is out of range
     */
    IDraftLine addLine(ItemStack template, int amount, double unitPrice);

    /**
     * Adds a line that accepts every item meeting criteria. The server checks the criteria when the order is created
     * and refuses unknown items, tags and enchantments.
     *
     * @param criteria  what the line accepts
     * @param amount    number of items, at least 1
     * @param unitPrice price of one item, not negative
     * @return the added line
     * @throws IllegalArgumentException when a value is out of range
     */
    IDraftLine addLine(ItemCriteria criteria, int amount, double unitPrice);

    /**
     * The number of items over all lines.
     *
     * @return number of items
     */
    int amount();

    /**
     * The sum of the line prices. The fee of the order is computed from this price after all listeners ran.
     *
     * @return price of the order without the fee
     */
    double price();
}
