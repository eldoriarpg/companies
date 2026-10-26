/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.systemorders;

/**
 * The price per item of a rule: a fixed one, or the market average times a factor. A random jitter is drawn around
 * it, and the result is kept within the bounds.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class RulePrice {
    private PriceMode mode = PriceMode.AVERAGE;
    private double value = 1.0;
    private double factor = 1.0;
    private double jitter = 0.0;
    private double min = 0.0;
    private double max = 0.0;

    public RulePrice() {}

    /**
     * @param mode   how the price is found
     * @param value  price per item for {@link PriceMode#FIXED}
     * @param factor factor on the market average for {@link PriceMode#AVERAGE}
     * @param jitter share the price may move either way at random
     * @param min    lowest price per item
     * @param max    highest price per item, 0 for no highest
     */
    public RulePrice(PriceMode mode, double value, double factor, double jitter, double min, double max) {
        this.mode = mode;
        this.value = value;
        this.factor = factor;
        this.jitter = jitter;
        this.min = min;
        this.max = max;
    }

    /**
     * @return how the price is found
     */
    public PriceMode mode() {
        return mode == null ? PriceMode.AVERAGE : mode;
    }

    /**
     * @return the price per item of {@link PriceMode#FIXED}, never negative
     */
    public double value() {
        return Math.max(0, value);
    }

    /**
     * @return the factor on the market average of {@link PriceMode#AVERAGE}, never negative
     */
    public double factor() {
        return Math.max(0, factor);
    }

    /**
     * How far the price moves at random either way, as a share: 0.05 is up to 5 % less or more.
     *
     * @return the share, between 0 and 0.9
     */
    public double jitter() {
        return Math.clamp(jitter, 0, 0.9);
    }

    /**
     * @return the lowest price per item, never negative
     */
    public double min() {
        return Math.max(0, min);
    }

    /**
     * @return the highest price per item, 0 when there is none
     */
    public double max() {
        return Math.max(0, max);
    }

    /**
     * How a rule finds its price.
     */
    public enum PriceMode {
        /**
         * A fixed price per item.
         */
        FIXED,
        /**
         * The average price per item players paid lately, times a factor.
         */
        AVERAGE
    }
}
