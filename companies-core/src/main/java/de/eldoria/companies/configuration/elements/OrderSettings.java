/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class OrderSettings {
    private int maxItems = 64 * 8;
    private int maxMaterials = 5;
    private int maxUnclaimedHours = 24 * 7;

    public OrderSettings() {
    }

    public int maxItems() {
        return maxItems;
    }

    public void maxItems(int maxItems) {
        this.maxItems = maxItems;
    }

    public int maxMaterials() {
        return maxMaterials;
    }

    public void maxMaterials(int maxMaterials) {
        this.maxMaterials = maxMaterials;
    }

    public int maxUnclaimedHours() {
        return maxUnclaimedHours;
    }

    public void maxUnclaimedHours(int maxUnclaimedHours) {
        this.maxUnclaimedHours = maxUnclaimedHours;
    }
}
