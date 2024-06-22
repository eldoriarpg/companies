/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
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
