package de.eldoria.companies.configuration.elements.ordersettings;

public enum FeeMode {
    /**
     * No fees applied
     */
    NONE,
    /**
     * The max value of percent or fixed
     */
    MAX,
    /**
     * A percentage of the sum
     */
    PERCENT,
    /**
     * A fixed amount
     */
    FIXED;
}
