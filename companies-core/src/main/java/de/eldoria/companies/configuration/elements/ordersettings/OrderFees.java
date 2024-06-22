/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.ordersettings;

import de.eldoria.companies.orders.OrderBuilder;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class OrderFees {
    private FeeMode mode = FeeMode.NONE;
    private double fixed = 200.0;
    private double percent = .1;

    public OrderFees() {
    }

    /**
     * Get the fees for the items contained in the order builder
     *
     * @param builder builder of the order
     * @return a double which represents the fee to publish this order.
     */
    public double orderFee(OrderBuilder builder) {
        double price = builder.price();
        return switch (mode) {
            case NONE -> .0;
            case MAX -> Math.max(fixed, price * percent);
            case PERCENT -> price * percent;
            case FIXED -> fixed;
        };
    }
    /**
     * Get the fees for the items contained in the order builder
     *
     * @param price price of the order
     * @return a double which represents the fee to publish this order.
     */
    public double orderFee(double price) {
        return switch (mode) {
            case NONE -> .0;
            case MAX -> Math.max(fixed, price * percent);
            case PERCENT -> price * percent;
            case FIXED -> fixed;
        };
    }



    public FeeMode mode() {
        return mode;
    }

    public double fixed() {
        return fixed;
    }

    public double percent() {
        return percent;
    }

    public void mode(FeeMode mode) {
        this.mode = mode;
    }

    public void fixed(double fixed) {
        this.fixed = fixed;
    }

    public void percent(double percent) {
        this.percent = percent;
    }
}
