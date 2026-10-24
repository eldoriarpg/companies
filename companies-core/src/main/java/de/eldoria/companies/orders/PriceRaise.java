/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Spreads a price increase over the lines of an order.
 * <p>
 * Each line gets a share in proportion to its price, or an equal share when every line is free. Shares are whole
 * cents, and the cents lost to rounding go to the first lines, so the shares always add up to the increase.
 */
public final class PriceRaise {
    private PriceRaise() {}

    /**
     * Splits an increase over the given line prices.
     *
     * @param prices   current price of every line
     * @param increase amount to add to the order, rounded down to cents
     * @return the amount to add to each line, in the order of {@code prices}
     */
    public static List<Double> split(List<Double> prices, double increase) {
        if (prices.isEmpty()) return List.of();
        var cents = BigDecimal.valueOf(increase)
                .setScale(2, RoundingMode.FLOOR)
                .movePointRight(2)
                .longValueExact();
        var weights = weights(prices);
        var totalWeight = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Long> shares = new ArrayList<>();
        var assigned = 0L;
        for (var weight : weights) {
            var share = BigDecimal.valueOf(cents)
                    .multiply(weight)
                    .divide(totalWeight, 0, RoundingMode.FLOOR)
                    .longValue();
            shares.add(share);
            assigned += share;
        }
        var remainder = cents - assigned;
        for (var i = 0; i < remainder; i++) {
            shares.set(i, shares.get(i) + 1);
        }
        return shares.stream()
                .map(share -> BigDecimal.valueOf(share).movePointLeft(2).doubleValue())
                .toList();
    }

    private static List<BigDecimal> weights(List<Double> prices) {
        var allFree = prices.stream().allMatch(price -> !(price > 0));
        return prices.stream()
                .map(price -> allFree ? BigDecimal.ONE : BigDecimal.valueOf(Math.max(0.0, price)))
                .toList();
    }
}
