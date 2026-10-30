/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a harvest job lets the company break and what goes into the stock.
 * <ul>
 *     <li>Field crops (wheat, carrots, potatoes, beetroots, nether wart, cocoa) only when ripe; they are replanted at
 *     once and one seed of the yield is kept for that.</li>
 *     <li>Sugar cane, bamboo and cactus only above their base block, which stays to grow again.</li>
 *     <li>Melons and pumpkins only the fruit, never the stem.</li>
 * </ul>
 * Every other block of a harvest area stays as it is.
 */
public final class HarvestRules {
    private static final Map<Material, Material> SEEDS = new EnumMap<>(Map.of(
            Material.WHEAT, Material.WHEAT_SEEDS,
            Material.CARROTS, Material.CARROT,
            Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS,
            Material.NETHER_WART, Material.NETHER_WART,
            Material.COCOA, Material.COCOA_BEANS));

    private static final Map<Material, Material> STACKED = new EnumMap<>(Map.of(
            Material.SUGAR_CANE, Material.SUGAR_CANE,
            Material.BAMBOO, Material.BAMBOO,
            Material.CACTUS, Material.CACTUS));

    private HarvestRules() {}

    /**
     * How a block of a harvest area is harvested.
     */
    public enum Crop {
        /**
         * A field crop, harvested when ripe and replanted.
         */
        FIELD,
        /**
         * Sugar cane, bamboo or cactus, harvested above the base block.
         */
        STACKED,
        /**
         * A melon or pumpkin, harvested whole.
         */
        FRUIT,
        /**
         * Anything else, which members may not break.
         */
        NONE
    }

    /**
     * @param type the type of a block
     * @return how it is harvested
     */
    public static Crop crop(Material type) {
        if (SEEDS.containsKey(type)) return Crop.FIELD;
        if (STACKED.containsKey(type)) return Crop.STACKED;
        if (type == Material.MELON || type == Material.PUMPKIN) return Crop.FRUIT;
        return Crop.NONE;
    }

    /**
     * @param type a stacked crop
     * @return the item one of its blocks yields
     */
    public static Material item(Material type) {
        return STACKED.getOrDefault(type, type);
    }

    /**
     * Turns the drops of a harvested block into what goes into the stock: a field crop keeps one seed back for the
     * replant.
     *
     * @param type  the type of the harvested block
     * @param drops what it would have dropped
     * @return the items, as amounts by material name
     */
    public static Map<String, Integer> yield(Material type, Collection<ItemStack> drops) {
        Map<String, Integer> items = new LinkedHashMap<>();
        for (var drop : drops) {
            if (drop == null || drop.getType().isAir() || drop.getAmount() <= 0) continue;
            items.merge(drop.getType().name(), drop.getAmount(), Integer::sum);
        }
        var seed = SEEDS.get(type);
        if (seed != null) {
            items.computeIfPresent(seed.name(), (name, amount) -> amount > 1 ? amount - 1 : null);
        }
        return items;
    }
}
