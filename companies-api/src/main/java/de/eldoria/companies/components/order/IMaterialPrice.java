/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.order;

import de.eldoria.companies.components.price.PriceBasis;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The prices of one item of a material: what past orders paid, and the baseline the shop plugins of the server give
 * it. {@link #price()} is the one Companies goes by.
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

    /**
     * How many delivered order lines the average, lowest and highest price come from.
     *
     * @return number of lines, at most the 99 latest
     */
    int orders();

    /**
     * The price of one item the shops of the server give it.
     *
     * @return the baseline, empty when no shop plugin knows the material
     */
    OptionalDouble baseline();

    /**
     * What the baseline was worked out from.
     *
     * @return the basis, empty when there is no baseline
     */
    Optional<PriceBasis> basis();

    /**
     * Whether {@link #price()} is the average of past orders rather than the baseline of the shops: the material was
     * delivered often enough for its orders to tell, as {@code market.minOrders} in {@code order.yml} says, or no shop
     * knows it.
     *
     * @return true when the price comes from orders
     */
    boolean fromOrders();

    /**
     * The price of one item Companies goes by, such as the price offered in the order builder.
     *
     * @return the average of past orders when {@link #fromOrders()}, otherwise the baseline; 0 when neither is known
     */
    double price();
}
