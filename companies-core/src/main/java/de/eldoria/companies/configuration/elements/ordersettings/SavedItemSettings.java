/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.ordersettings;

/**
 * The items players save to order again, the {@code savedItems} section of {@code order.yml}.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class SavedItemSettings {
    private int max = 27;

    public SavedItemSettings() {}

    /**
     * @param max how many items a player may save
     */
    public SavedItemSettings(int max) {
        this.max = max;
    }

    /**
     * How many items a player may save. At least 0, which switches saving off.
     *
     * @return the maximum
     */
    public int max() {
        return Math.max(0, max);
    }
}
