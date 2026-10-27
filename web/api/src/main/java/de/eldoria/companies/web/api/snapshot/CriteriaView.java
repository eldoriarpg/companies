/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

import java.util.List;

/**
 * What a criteria line accepts: any item of a set, optionally with enchantment conditions.
 *
 * @param items the materials and item tags of the set
 * @param minEnchantments the fewest enchantments an item must carry, 0 for no condition
 * @param enchantments the enchantments an item must carry, each at a minimum level
 */
public record CriteriaView(List<Entry> items, int minEnchantments, List<Required> enchantments) {
    /**
     * A material or item tag of the set.
     *
     * @param key the key, e.g. {@code minecraft:oak_log}, or {@code #minecraft:logs} for a tag
     * @param name the display name as plain text
     * @param tag whether the entry is an item tag
     */
    public record Entry(String key, String name, boolean tag) {}

    /**
     * An enchantment an item must carry.
     *
     * @param key the enchantment key, e.g. {@code minecraft:sharpness}
     * @param name the display name as plain text
     * @param minLevel the lowest level accepted
     */
    public record Required(String key, String name, int minLevel) {}
}
