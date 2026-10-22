/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.ordersettings;

import de.eldoria.companies.util.Money;

import java.math.BigDecimal;

/**
 * The fee charged for placing an order: a fixed part plus a percentage of the price, clamped by an optional minimum
 * and maximum. A bound of 0 is not applied, so a configuration of zeros charges no fee.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class OrderFee {
    private double fixed = 0.0;
    private double percent = 0.0;
    private double min = 0.0;
    private double max = 0.0;

    public OrderFee() {}

    /**
     * Creates a fee with the given parts.
     *
     * @param fixed   fixed part
     * @param percent percentage of the price, where 5 means 5 %
     * @param min     lower bound, 0 for none
     * @param max     upper bound, 0 for none
     */
    public OrderFee(double fixed, double percent, double min, double max) {
        this.fixed = fixed;
        this.percent = percent;
        this.min = min;
        this.max = max;
    }

    /**
     * The fee for an order of the given price, rounded down to two decimals and never negative.
     *
     * @param price price of the order
     * @return fee for the order
     */
    public double of(double price) {
        var fee = BigDecimal.valueOf(fixed).add(Money.percentOf(price, percent)).doubleValue();
        if (min > 0) fee = Math.max(fee, min);
        if (max > 0) fee = Math.min(fee, max);
        return Math.max(0.0, Money.floor(fee));
    }

    /**
     * The fee for raising the price of an order: the percentage of the increase, with the minimum and maximum applied
     * to the order's total fee. The fixed part was paid when the order was placed and is not charged again.
     *
     * @param currentFee fee the order already carries
     * @param increase   amount the price is raised by
     * @return additional fee, rounded down to two decimals and never negative
     */
    public double raise(double currentFee, double increase) {
        var current = BigDecimal.valueOf(currentFee);
        var total = current.add(Money.percentOf(increase, percent)).doubleValue();
        if (min > 0) total = Math.max(total, min);
        if (max > 0) total = Math.min(total, max);
        return Math.max(
                0.0, Money.floor(BigDecimal.valueOf(total).subtract(current).doubleValue()));
    }

    public double fixed() {
        return fixed;
    }

    public double percent() {
        return percent;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }
}
