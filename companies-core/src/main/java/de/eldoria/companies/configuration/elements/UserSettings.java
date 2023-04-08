/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
public class UserSettings {
    private int maxOrders = 7;

    public UserSettings() {
    }

    public int maxOrders() {
        return maxOrders;
    }

    public void maxOrders(int maxOrders) {
        this.maxOrders = maxOrders;
    }
}
