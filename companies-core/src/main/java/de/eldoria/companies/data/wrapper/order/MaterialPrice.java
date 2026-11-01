/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

import de.eldoria.companies.components.order.IMaterialPrice;
import de.eldoria.companies.components.price.PriceBasis;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The prices of one item of a material: the average, lowest and highest of the latest delivered order lines, and the
 * baseline of the server's shops. The price Companies goes by is the average of the orders once there are at least
 * {@code minOrders} of them or no shop knows the material, and the baseline below that.
 */
public final class MaterialPrice implements IMaterialPrice {
    private final String material;
    private final double avgPrice;
    private final double minPrice;
    private final double maxPrice;
    private final int orders;
    private final double baseline;
    private final @Nullable PriceBasis basis;
    private final int minOrders;

    /**
     * A material nothing is known of.
     *
     * @param material name of the material
     */
    public MaterialPrice(String material) {
        this(material, 0, 0, 0, 0, 0, null, 1);
    }

    /**
     * @param material  name of the material
     * @param avgPrice  average price of one item in the latest delivered lines, 0 for none
     * @param minPrice  lowest price of one item in them
     * @param maxPrice  highest price of one item in them
     * @param orders    number of lines the prices come from
     * @param baseline  the baseline of the shops, 0 for none
     * @param basis     what the baseline was worked out from, null when there is none
     * @param minOrders the fewest lines the average is gone by when the shops know the material
     */
    public MaterialPrice(
            String material,
            double avgPrice,
            double minPrice,
            double maxPrice,
            int orders,
            double baseline,
            @Nullable PriceBasis basis,
            int minOrders) {
        this.material = material;
        this.avgPrice = avgPrice;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.orders = orders;
        this.baseline = basis == null ? 0 : baseline;
        this.basis = this.baseline > 0 ? basis : null;
        this.minOrders = minOrders;
    }

    /**
     * Prices of orders only, as many as the average is gone by.
     *
     * @param material name of the material
     * @param avgPrice average price of one item
     * @param minPrice lowest price of one item
     * @param maxPrice highest price of one item
     * @return the prices
     */
    public static MaterialPrice ofOrders(String material, double avgPrice, double minPrice, double maxPrice) {
        return new MaterialPrice(material, avgPrice, minPrice, maxPrice, 1, 0, null, 1);
    }

    /**
     * The baseline of the shops only, for a material no order delivered.
     *
     * @param material name of the material
     * @param baseline the baseline
     * @param basis    what it was worked out from
     * @return the prices
     */
    public static MaterialPrice ofShops(String material, double baseline, PriceBasis basis) {
        return new MaterialPrice(material, 0, 0, 0, 0, baseline, basis, 1);
    }

    /**
     * @param minOrders the fewest lines the average is gone by when the shops know the material
     * @return these prices, choosing between orders and shops by the given number
     */
    public MaterialPrice withMinOrders(int minOrders) {
        if (minOrders == this.minOrders) return this;
        return new MaterialPrice(material, avgPrice, minPrice, maxPrice, orders, baseline, basis, minOrders);
    }

    @Override
    public String material() {
        return material;
    }

    @Override
    public double avgPrice() {
        return avgPrice;
    }

    @Override
    public double minPrice() {
        return minPrice;
    }

    @Override
    public double maxPrice() {
        return maxPrice;
    }

    @Override
    public int orders() {
        return orders;
    }

    @Override
    public OptionalDouble baseline() {
        return baseline > 0 ? OptionalDouble.of(baseline) : OptionalDouble.empty();
    }

    @Override
    public Optional<PriceBasis> basis() {
        return Optional.ofNullable(basis);
    }

    @Override
    public boolean fromOrders() {
        return avgPrice > 0 && (orders >= minOrders || baseline <= 0);
    }

    @Override
    public double price() {
        if (fromOrders()) return avgPrice;
        return baseline;
    }

    /**
     * Whether a price is known for this material.
     *
     * @return true when orders or the shops give it a price above zero
     */
    public boolean hasData() {
        return price() > 0;
    }

    @Override
    public String toString() {
        return "MaterialPrice{material='" + material + "', avgPrice=" + avgPrice + ", minPrice=" + minPrice
                + ", maxPrice=" + maxPrice + ", orders=" + orders + ", baseline=" + baseline + ", basis=" + basis
                + '}';
    }
}
