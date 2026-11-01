/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What a criteria line accepts: any item of a set, optionally with enchantment, potion and durability conditions.
 *
 * @param items the materials and item tags of the set
 * @param minEnchantments the fewest enchantments an item must carry, 0 for no condition
 * @param enchantments the enchantments an item must carry, each at a minimum level
 * @param potion the potion type an item must hold, none for no condition
 * @param minDurability the least share of durability left in percent, 0 for no condition
 */
public record CriteriaView(
        List<Entry> items,
        int minEnchantments,
        List<Required> enchantments,
        @Nullable Potion potion,
        int minDurability) {
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

    /**
     * A potion type an item must hold.
     *
     * @param key the potion type key, e.g. {@code minecraft:long_swiftness}
     * @param name the display name as plain text
     */
    public record Potion(String key, String name) {}
}
