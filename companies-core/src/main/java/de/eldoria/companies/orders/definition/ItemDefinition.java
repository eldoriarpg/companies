/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.definition;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * An item defined on the dashboard to be ordered exactly: a material and what survival can give it.
 *
 * @param material     the material's key, e.g. {@code minecraft:diamond_sword}
 * @param enchantments enchantment levels by the enchantment's key; on an enchanted book they are stored enchantments
 * @param potion       the potion type's key, for potions and tipped arrows
 * @param dye          the dye colour's name, e.g. {@code RED}, for dyeable leather items
 * @param trim         the armour trim, for armour
 */
public record ItemDefinition(
        String material,
        Map<String, Integer> enchantments,
        @Nullable String potion,
        @Nullable String dye,
        @Nullable Trim trim) {
    public ItemDefinition {
        enchantments = enchantments == null ? Map.of() : Map.copyOf(enchantments);
    }

    /**
     * An armour trim.
     *
     * @param pattern  the trim pattern's key, e.g. {@code minecraft:coast}
     * @param material the trim material's key, e.g. {@code minecraft:gold}
     */
    public record Trim(String pattern, String material) {}
}
