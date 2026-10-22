/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

public class MaterialPrice implements de.eldoria.companies.components.order.IMaterialPrice {
    private final String material;
    private final double avgPrice;
    private final double minPrice;
    private final double maxPrice;

    public MaterialPrice(String material) {
        this.material = material;
        avgPrice = 0.0;
        minPrice = 0.0;
        maxPrice = 0.0;
    }

    public MaterialPrice(String material, double avgPrice, double minPrice, double maxPrice) {
        this.material = material;
        this.avgPrice = avgPrice;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
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

    /**
     * Whether the market knows a price for this material.
     *
     * @return true when the average unit price is above zero
     */
    public boolean hasData() {
        return avgPrice > 0;
    }

    @Override
    public String toString() {
        return "MaterialPrice{" + "material='"
                + material + "'" + ", avgPrice="
                + avgPrice + ", minPrice="
                + minPrice + ", maxPrice="
                + maxPrice + '}';
    }
}
