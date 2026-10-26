/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import de.eldoria.companies.configuration.elements.systemorders.RulePrice;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.DoubleSupplier;

/**
 * Finds the price per item of a defined rule: the fixed price or the market average times the factor, moved by a
 * random share within the jitter, then kept within the bounds and rounded to cents.
 */
public final class RulePricing {
    private RulePricing() {}

    /**
     * @param price   the rule's price
     * @param average the market average per item of the material, empty when there is none
     * @param random  a random number from 0 included to 1 excluded
     * @return the price and how it was found, empty when an average price has no market average or the price is not
     *         positive
     */
    public static Optional<Priced> price(RulePrice price, OptionalDouble average, DoubleSupplier random) {
        Double market = null;
        double base;
        if (price.mode() == RulePrice.PriceMode.FIXED) {
            base = price.value();
        } else {
            if (average.isEmpty() || !(average.getAsDouble() > 0)) return Optional.empty();
            market = average.getAsDouble();
            base = market * price.factor();
        }
        var jitter = price.jitter() * (random.getAsDouble() * 2 - 1);
        var jittered = base * (1 + jitter);
        var bounded = Math.max(jittered, price.min());
        if (price.max() > 0) bounded = Math.min(bounded, price.max());
        var unit = cents(bounded);
        if (!(unit > 0) || !Double.isFinite(unit)) return Optional.empty();
        return Optional.of(new Priced(market, base, jitter, jittered, unit));
    }

    private static double cents(double value) {
        if (!Double.isFinite(value)) return value;
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * A price per item and how it was found.
     *
     * @param average  the market average it stood on, null for a fixed price
     * @param base     the fixed price or the average times the factor
     * @param jitter   the share drawn within the jitter, such as -0.03
     * @param jittered the base moved by the jitter
     * @param unit     the price per item after the bounds, in cents
     */
    public record Priced(@Nullable Double average, double base, double jitter, double jittered, double unit) {
        /**
         * @return true when a bound changed the price
         */
        public boolean bounded() {
            return Math.abs(cents(jittered) - unit) >= 0.005;
        }
    }
}
