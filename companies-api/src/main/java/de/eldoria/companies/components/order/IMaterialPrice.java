/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.order;

/**
 * The prices of one item of a material in past orders.
 */
public interface IMaterialPrice {
    /**
     * The material the prices are for.
     *
     * @return name of the material
     */
    String material();

    /**
     * The average price of one item.
     *
     * @return average price, 0 when the material was never ordered
     */
    double avgPrice();

    /**
     * The lowest price of one item.
     *
     * @return lowest price, 0 when the material was never ordered
     */
    double minPrice();

    /**
     * The highest price of one item.
     *
     * @return highest price, 0 when the material was never ordered
     */
    double maxPrice();
}
