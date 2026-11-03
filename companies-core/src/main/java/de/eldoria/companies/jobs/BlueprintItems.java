/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The items the blocks of a blueprint are placed with, as a player would place them. A block takes the item it is
 * placed with: a wall torch a torch, redstone wire redstone, a crop or stem its seeds, a vine's body the vine. The
 * second half of a block of two (a door's top, a bed's head, a tall plant's top) takes nothing, a double slab two
 * slabs, a block of several (candles, sea pickles, turtle eggs, petals, snow layers, lichen and vine faces) one item
 * each, a potted plant a pot and the plant, a candle cake a cake and the candle.
 * <p>
 * Fluids are counted apart, as only owners who supply materials need them: water takes {@value #WATER_BUCKETS} water
 * buckets for the whole blueprint, however many sources, waterlogged blocks and water plants it has, since two sources
 * make an infinite one; lava takes a lava bucket per source. They come from the blueprint's {@link Blueprint#fluids()},
 * as its blocks never ask for them.
 * <p>
 * Blocks no player places from an item are made with tools as in vanilla, such as farmland and paths, or can not be
 * made by any player at all, such as spawners and reinforced deepslate.
 */
public final class BlueprintItems {
    /**
     * The water buckets a blueprint with water takes.
     */
    public static final int WATER_BUCKETS = 2;

    private static final Set<Material> UNMAKEABLE = unmakeable();
    private static final Set<Material> BY_HAND =
            EnumSet.of(Material.FARMLAND, Material.DIRT_PATH, Material.FROSTED_ICE, Material.CHORUS_PLANT);
    private static final Set<Material> UNDERWATER =
            EnumSet.of(Material.KELP, Material.KELP_PLANT, Material.SEAGRASS, Material.TALL_SEAGRASS);
    private static final Set<Material> FACES =
            EnumSet.of(Material.GLOW_LICHEN, Material.SCULK_VEIN, Material.RESIN_CLUMP, Material.VINE);
    private static final List<String> COUNTS =
            List.of("candles", "pickles", "eggs", "flower_amount", "segment_amount", "layers");
    private static final List<String> SIDES = List.of("north", "south", "east", "west", "up", "down");
    private static final Map<Material, Material> BODIES = bodies();

    private BlueprintItems() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * What the blocks of a blueprint take.
     *
     * @param items      the items they are placed with, by item, in the order the blueprint first asks for them
     * @param fluids     the buckets its water and lava take, by bucket
     * @param byHand     the block types no item places that are made as in vanilla, such as farmland
     * @param unmakeable the block types no player can make, such as spawners
     */
    public record Needs(
            Map<Material, Integer> items,
            Map<Material, Integer> fluids,
            Set<Material> byHand,
            Set<Material> unmakeable) {
        /**
         * Keeps the parts unchangeable and in their order.
         */
        public Needs {
            items = Collections.unmodifiableMap(new LinkedHashMap<>(items));
            fluids = Collections.unmodifiableMap(new LinkedHashMap<>(fluids));
            byHand = Collections.unmodifiableSet(new LinkedHashSet<>(byHand));
            unmakeable = Collections.unmodifiableSet(new LinkedHashSet<>(unmakeable));
        }
    }

    /**
     * Items one block takes when it is placed.
     *
     * @param item   the item
     * @param amount how many of it
     */
    public record Take(Material item, int amount) {}

    /**
     * @param blueprint a blueprint
     * @return what its blocks take
     */
    public static Needs of(Blueprint blueprint) {
        return of(blueprint, BlueprintItems::placedWith);
    }

    /**
     * @param blueprint  a blueprint
     * @param placedWith the item a block is placed with, null for a block no item places
     * @return what its blocks take
     */
    static Needs of(Blueprint blueprint, Function<Blueprint.Want, @Nullable Material> placedWith) {
        Map<Material, Integer> items = new LinkedHashMap<>();
        Set<Material> byHand = new LinkedHashSet<>();
        Set<Material> unmakeable = new LinkedHashSet<>();
        var water = false;
        for (var entry : blueprint.wants().entrySet()) {
            var want = entry.getKey();
            if (want.type().isAir()) continue;
            if (UNDERWATER.contains(want.type())
                    || "true".equals(Blueprint.states(want.data()).get("waterlogged"))) {
                water = true;
            }
            if (unmakeable(want.type())) {
                unmakeable.add(want.type());
                continue;
            }
            var takes = take(want, placedWith);
            if (takes.isEmpty()) {
                if (!half(want)) byHand.add(want.type());
                continue;
            }
            for (var take : takes) items.merge(take.item(), take.amount() * entry.getValue(), Integer::sum);
        }
        Map<Material, Integer> fluids = new LinkedHashMap<>();
        for (var fluid : blueprint.fluids()) {
            if (fluid.type() == Material.WATER) {
                water = true;
            } else {
                fluids.merge(Material.LAVA_BUCKET, 1, Integer::sum);
            }
        }
        if (water) fluids.put(Material.WATER_BUCKET, WATER_BUCKETS);
        return new Needs(items, fluids, byHand, unmakeable);
    }

    /**
     * @param want a block of a blueprint, or a lava source
     * @return the items placing it takes from a stock, empty when it takes none: a second half, water, flowing lava, a
     *     block made by hand or by nobody
     */
    public static List<Take> take(Blueprint.Want want) {
        return take(want, BlueprintItems::placedWith);
    }

    private static List<Take> take(Blueprint.Want want, Function<Blueprint.Want, @Nullable Material> placedWith) {
        var type = want.type();
        if (type.isAir() || type == Material.WATER || half(want)) return List.of();
        if (type == Material.LAVA) return flowing(want) ? List.of() : List.of(new Take(Material.LAVA_BUCKET, 1));
        if (unmakeable(type) || BY_HAND.contains(type)) return List.of();
        var name = type.name();
        if (name.startsWith("POTTED_")) return potted(name.substring("POTTED_".length()));
        if (name.endsWith("CANDLE_CAKE")) {
            var candle = Material.matchMaterial(name.substring(0, name.length() - "_CAKE".length()));
            return candle == null
                    ? List.of(new Take(Material.CAKE, 1))
                    : List.of(new Take(Material.CAKE, 1), new Take(candle, 1));
        }
        var item = BODIES.containsKey(type) ? BODIES.get(type) : placedWith.apply(want);
        if (item == null) return List.of();
        return List.of(new Take(item, amount(want)));
    }

    /**
     * @param type a block type
     * @return whether no player can make it, so a blueprint's spots of it are left out where materials are supplied
     */
    public static boolean unmakeable(Material type) {
        return UNMAKEABLE.contains(type);
    }

    /**
     * @param want a block of a blueprint
     * @return the item it is placed with, such as redstone for a wire or a torch for a wall torch, null for none
     */
    static @Nullable Material placedWith(Blueprint.Want want) {
        Material item;
        try {
            item = Bukkit.createBlockData(want.data()).getPlacementMaterial();
        } catch (RuntimeException e) {
            item = want.type();
        }
        return item.isItem() && !item.isAir() ? item : null;
    }

    private static int amount(Blueprint.Want want) {
        var states = Blueprint.states(want.data());
        if ("double".equals(states.get("type"))) return 2;
        if (FACES.contains(want.type())) {
            var faces = (int) SIDES.stream()
                    .filter(side -> "true".equals(states.get(side)))
                    .count();
            return Math.max(1, faces);
        }
        for (var count : COUNTS) {
            var value = states.get(count);
            if (value == null) continue;
            try {
                return Math.max(1, Integer.parseInt(value));
            } catch (NumberFormatException e) {
                return 1;
            }
        }
        return 1;
    }

    private static List<Take> potted(String plant) {
        List<Take> takes = new ArrayList<>(List.of(new Take(Material.FLOWER_POT, 1)));
        var item =
                switch (plant) {
                    case "AZALEA_BUSH" -> Material.AZALEA;
                    case "FLOWERING_AZALEA_BUSH" -> Material.FLOWERING_AZALEA;
                    default -> Material.matchMaterial(plant.toLowerCase(Locale.ROOT));
                };
        if (item != null && item.isItem()) takes.add(new Take(item, 1));
        return takes;
    }

    private static boolean half(Blueprint.Want want) {
        var states = Blueprint.states(want.data());
        return "upper".equals(states.get("half")) || "head".equals(states.get("part"));
    }

    private static boolean flowing(Blueprint.Want want) {
        var level = Blueprint.states(want.data()).get("level");
        return level != null && !"0".equals(level);
    }

    private static Map<Material, Material> bodies() {
        Map<Material, Material> bodies = new EnumMap<>(Material.class);
        bodies.put(Material.KELP_PLANT, Material.KELP);
        bodies.put(Material.CAVE_VINES, Material.GLOW_BERRIES);
        bodies.put(Material.CAVE_VINES_PLANT, Material.GLOW_BERRIES);
        bodies.put(Material.WEEPING_VINES_PLANT, Material.WEEPING_VINES);
        bodies.put(Material.TWISTING_VINES_PLANT, Material.TWISTING_VINES);
        bodies.put(Material.BIG_DRIPLEAF_STEM, Material.BIG_DRIPLEAF);
        bodies.put(Material.BAMBOO_SAPLING, Material.BAMBOO);
        bodies.put(Material.PUMPKIN_STEM, Material.PUMPKIN_SEEDS);
        bodies.put(Material.ATTACHED_PUMPKIN_STEM, Material.PUMPKIN_SEEDS);
        bodies.put(Material.MELON_STEM, Material.MELON_SEEDS);
        bodies.put(Material.ATTACHED_MELON_STEM, Material.MELON_SEEDS);
        return bodies;
    }

    private static Set<Material> unmakeable() {
        Set<Material> types = EnumSet.of(
                Material.SPAWNER,
                Material.TRIAL_SPAWNER,
                Material.VAULT,
                Material.REINFORCED_DEEPSLATE,
                Material.BUDDING_AMETHYST,
                Material.STRUCTURE_VOID,
                Material.PETRIFIED_OAK_SLAB,
                Material.SUSPICIOUS_SAND,
                Material.SUSPICIOUS_GRAVEL,
                Material.FROGSPAWN,
                Material.PLAYER_HEAD,
                Material.PLAYER_WALL_HEAD,
                Material.DRAGON_EGG);
        for (var type : Material.values()) {
            if (!type.isLegacy() && type.name().startsWith("INFESTED_")) types.add(type);
        }
        return types;
    }
}
