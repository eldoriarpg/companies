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
 *     <li>Sugar cane, bamboo, cactus and kelp only above their base block, which stays to grow again.</li>
 *     <li>Melons and pumpkins only the fruit, never the stem.</li>
 *     <li>Sweet berries and glow berries are picked, not broken: using a ripe bush or a vine with berries takes them
 *     and leaves the plant to grow again.</li>
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
            Material.CACTUS, Material.CACTUS,
            Material.KELP, Material.KELP,
            Material.KELP_PLANT, Material.KELP));

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
         * A sweet berry bush or a glow berry vine, picked by using it.
         */
        PICKED,
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
        if (type == Material.SWEET_BERRY_BUSH || type == Material.CAVE_VINES || type == Material.CAVE_VINES_PLANT) {
            return Crop.PICKED;
        }
        return Crop.NONE;
    }

    /**
     * @param first  a stacked crop
     * @param second another block type
     * @return whether both belong to the same plant, as kelp and the kelp stem below its top do
     */
    public static boolean sameStack(Material first, Material second) {
        var item = STACKED.get(first);
        return item != null && item == STACKED.get(second);
    }

    /**
     * @param type a stacked crop
     * @return what is left where a block of it is taken: water for kelp, air for the rest
     */
    public static Material emptied(Material type) {
        return item(type) == Material.KELP ? Material.WATER : Material.AIR;
    }

    /**
     * @param type     a picked crop
     * @param age      the age of a sweet berry bush, ignored for vines
     * @param berries  whether a glow berry vine carries berries, ignored for bushes
     * @param bonus    whether a ripe bush gives one berry more
     * @return the berries picked, empty when the plant has none yet
     */
    public static Map<String, Integer> picked(Material type, int age, boolean berries, boolean bonus) {
        if (type == Material.SWEET_BERRY_BUSH) {
            if (age < 2) return Map.of();
            return Map.of(Material.SWEET_BERRIES.name(), (age >= 3 ? 2 : 1) + (bonus ? 1 : 0));
        }
        if ((type == Material.CAVE_VINES || type == Material.CAVE_VINES_PLANT) && berries) {
            return Map.of(Material.GLOW_BERRIES.name(), 1);
        }
        return Map.of();
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
