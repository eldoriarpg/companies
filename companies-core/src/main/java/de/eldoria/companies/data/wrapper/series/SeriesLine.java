/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.series;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.eldoria.companies.orders.OrderBuilder;
import org.bukkit.inventory.ItemStack;

/**
 * One line of a repeating order, posted unchanged with every copy. It stores the template stack, so lines requesting
 * an exact item repeat exactly.
 *
 * @param template  requested item with an amount of one
 * @param exact     whether the data of the item is requested as well
 * @param amount    number of items
 * @param unitPrice price of one item
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeriesLine(ItemStack template, boolean exact, int amount, double unitPrice) {
    /**
     * The line of a draft.
     *
     * @param line draft line
     * @return series line
     */
    public static SeriesLine of(OrderBuilder.DraftLine line) {
        return new SeriesLine(line.template(), line.exact(), line.amount(), line.unitPrice());
    }

    /**
     * This line as draft line, to post a copy.
     *
     * @return draft line
     */
    public OrderBuilder.DraftLine toDraftLine() {
        return new OrderBuilder.DraftLine(template, exact, amount, unitPrice);
    }
}
