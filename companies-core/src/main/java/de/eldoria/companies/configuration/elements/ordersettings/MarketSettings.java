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
    private int historyDays = 365;

    public MarketSettings() {}

    /**
     * @param minOrders orders a material needs before its prices are shown
     */
    public MarketSettings(int minOrders) {
        this.minOrders = minOrders;
    }

    /**
     * @param minOrders   orders a material needs before its prices are shown
     * @param historyDays days the price history is kept
     */
    public MarketSettings(int minOrders, int historyDays) {
        this.minOrders = minOrders;
        this.historyDays = historyDays;
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

    /**
     * How many days the price history is kept, today included. At least 14, so a change can always be computed.
     *
     * @return the days kept
     */
    public int historyDays() {
        return Math.max(14, historyDays);
    }
}
