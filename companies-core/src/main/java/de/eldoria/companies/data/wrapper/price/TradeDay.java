/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.price;

import de.eldoria.companies.components.price.ShopOrigin;
import de.eldoria.companies.components.price.ShopSide;

import java.time.LocalDate;

/**
 * What the shop trades of one material, side and origin of a source add up to on one day.
 *
 * @param day      the day, in UTC
 * @param source   id of the source, lower case
 * @param material name of the material, upper case
 * @param side     whether the players bought or sold
 * @param origin   who set the prices
 * @param trades   number of trades
 * @param items    number of items traded
 * @param total    money paid for all items
 * @param minUnit  lowest price of one item
 * @param maxUnit  highest price of one item
 */
public record TradeDay(
        LocalDate day,
        String source,
        String material,
        ShopSide side,
        ShopOrigin origin,
        int trades,
        long items,
        double total,
        double minUnit,
        double maxUnit) {
    /**
     * @return the average price of one item
     */
    public double unit() {
        return items == 0 ? 0 : total / items;
    }

    /**
     * Adds the trades of another total of the same day, material, side and origin.
     *
     * @param other the other total
     * @return the sum of both
     */
    public TradeDay plus(TradeDay other) {
        return new TradeDay(
                day,
                source,
                material,
                side,
                origin,
                trades + other.trades,
                items + other.items,
                total + other.total,
                Math.min(minUnit, other.minUnit),
                Math.max(maxUnit, other.maxUnit));
    }
}
