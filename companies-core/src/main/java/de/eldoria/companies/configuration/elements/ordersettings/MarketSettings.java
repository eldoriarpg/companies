/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.ordersettings;

/**
 * The market statistics shown on the dashboard, the {@code market} section of {@code order.yml}.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class MarketSettings {
    private int minOrders = 3;

    public MarketSettings() {}

    /**
     * @param minOrders orders a material needs before its prices are shown
     */
    public MarketSettings(int minOrders) {
        this.minOrders = minOrders;
    }

    /**
     * How many completed orders a material needs before its prices are shown, so a single order cannot be traced
     * back. At least 1.
     *
     * @return the minimum number of orders
     */
    public int minOrders() {
        return Math.max(1, minOrders);
    }
}
