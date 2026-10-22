/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import java.util.ArrayList;
import java.util.List;

/**
 * How delivered items are matched against the items an order requests.
 * <p>
 * Ignored components never decide a match and are stripped from items put into an order. Damaged items are only
 * accepted when {@code allowDamaged} is on; the owner then receives them as delivered.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class ItemMatchSettings {
    private List<String> ignoredComponents =
            new ArrayList<>(List.of("minecraft:custom_name", "minecraft:repair_cost", "minecraft:lore"));
    private boolean allowDamaged = false;

    public ItemMatchSettings() {}

    /**
     * Creates settings with the given values.
     *
     * @param ignoredComponents keys of the data component types to ignore
     * @param allowDamaged      whether damaged items may be delivered
     */
    public ItemMatchSettings(List<String> ignoredComponents, boolean allowDamaged) {
        this.ignoredComponents = new ArrayList<>(ignoredComponents);
        this.allowDamaged = allowDamaged;
    }

    /**
     * Keys of the data component types that never decide a match, like {@code minecraft:custom_name}.
     *
     * @return component keys
     */
    public List<String> ignoredComponents() {
        return List.copyOf(ignoredComponents);
    }

    /**
     * Whether damaged items may be delivered.
     *
     * @return true when damaged items are accepted
     */
    public boolean allowDamaged() {
        return allowDamaged;
    }
}
