/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * One line of an {@link OrderRequest}: the requested item, how many of it and the price of one.
 * <p>
 * A material line accepts every item of its material. An exact line accepts only items that match its template,
 * following the item matching settings of the server.
 *
 * @param template  requested item with an amount of one; a copy is returned
 * @param exact     whether the data of the template is requested as well, not only its material
 * @param amount    number of items, at least 1
 * @param unitPrice price of one item, not negative
 */
public record OrderLineRequest(ItemStack template, boolean exact, int amount, double unitPrice) {
    /**
     * Validates and copies the line.
     *
     * @throws IllegalArgumentException when the template is air or not an item, the amount is below 1 or the unit
     *                                  price is negative or not finite
     */
    public OrderLineRequest {
        Objects.requireNonNull(template, "template");
        if (template.getType().isAir() || !template.getType().isItem()) {
            throw new IllegalArgumentException("The template must be an item, was " + template.getType());
        }
        if (amount < 1) throw new IllegalArgumentException("The amount must be at least 1, was " + amount);
        if (!Double.isFinite(unitPrice) || unitPrice < 0) {
            throw new IllegalArgumentException("The unit price must be finite and not negative, was " + unitPrice);
        }
        template = template.asOne();
    }

    /**
     * A line that accepts every item of a material.
     *
     * @param material  requested material
     * @param amount    number of items, at least 1
     * @param unitPrice price of one item, not negative
     * @return the line
     * @throws IllegalArgumentException when the material is not an item or a value is out of range
     */
    public static OrderLineRequest material(Material material, int amount, double unitPrice) {
        Objects.requireNonNull(material, "material");
        if (!material.isItem() || material.isAir()) {
            throw new IllegalArgumentException("The material must be an item, was " + material);
        }
        return new OrderLineRequest(ItemStack.of(material), false, amount, unitPrice);
    }

    /**
     * A line that accepts only items matching a template.
     *
     * @param template  requested item; its amount is ignored
     * @param amount    number of items, at least 1
     * @param unitPrice price of one item, not negative
     * @return the line
     * @throws IllegalArgumentException when the template is not an item or a value is out of range
     */
    public static OrderLineRequest exact(ItemStack template, int amount, double unitPrice) {
        return new OrderLineRequest(template, true, amount, unitPrice);
    }

    /**
     * The requested item with an amount of one.
     *
     * @return a copy of the template
     */
    @Override
    public ItemStack template() {
        return template.clone();
    }

    /**
     * The price of the whole line.
     *
     * @return unit price times amount, before the server rounds it
     */
    public double price() {
        return unitPrice * amount;
    }
}
