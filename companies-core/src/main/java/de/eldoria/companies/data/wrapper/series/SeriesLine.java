/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.series;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.eldoria.companies.components.order.ItemCriteria;
import de.eldoria.companies.orders.OrderBuilder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * One line of a repeating order, posted unchanged with every copy. It stores the template stack and the criteria, so
 * lines requesting an exact item or criteria repeat exactly.
 *
 * @param template  requested item with an amount of one
 * @param exact     whether the data of the item is requested as well
 * @param amount    number of items
 * @param unitPrice price of one item
 * @param criteria  what a criteria line accepts, null for material and exact lines
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeriesLine(
        ItemStack template,
        boolean exact,
        int amount,
        double unitPrice,
        @Nullable ItemCriteria criteria) {
    /**
     * A material or exact line.
     *
     * @param template  requested item with an amount of one
     * @param exact     whether the data of the item is requested as well
     * @param amount    number of items
     * @param unitPrice price of one item
     */
    public SeriesLine(ItemStack template, boolean exact, int amount, double unitPrice) {
        this(template, exact, amount, unitPrice, null);
    }

    /**
     * The line of a draft.
     *
     * @param line draft line
     * @return series line
     */
    public static SeriesLine of(OrderBuilder.DraftLine line) {
        return new SeriesLine(line.template(), line.exact(), line.amount(), line.unitPrice(), line.criteria());
    }

    /**
     * This line as draft line, to post a copy.
     *
     * @return draft line
     */
    public OrderBuilder.DraftLine toDraftLine() {
        return new OrderBuilder.DraftLine(template, exact, amount, unitPrice, criteria);
    }
}
