/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.order;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What a criteria line accepts: any item of a set of materials and item tags, optionally with enchantment conditions.
 * <p>
 * The amount of the line is filled by any mix of matching items, so 64 of {@code #minecraft:logs} may be 40 oak and
 * 24 birch logs. Enchanted books count their stored enchantments.
 * <p>
 * Entries are normalized: a material is its upper case name such as {@code OAK_LOG}, a tag is {@code #} followed by
 * its lower case key such as {@code #minecraft:logs}. The minimum enchantment count is at least the number of required
 * enchantments.
 *
 * @param items           materials and item tags, at least one
 * @param minEnchantments the fewest enchantments an item must carry, 0 for no condition
 * @param enchantments    enchantments an item must carry, each at a minimum level
 */
public record ItemCriteria(List<String> items, int minEnchantments, List<Required> enchantments) {
    /**
     * Validates and normalizes the criteria.
     *
     * @throws IllegalArgumentException when no item is given, an entry is blank or the count is negative
     */
    public ItemCriteria {
        Objects.requireNonNull(items, "items");
        Set<String> normalized = new LinkedHashSet<>();
        for (var item : items) normalized.add(normalize(item));
        if (normalized.isEmpty()) throw new IllegalArgumentException("Criteria need at least one item");
        items = List.copyOf(normalized);
        if (minEnchantments < 0) {
            throw new IllegalArgumentException("The enchantment count must not be negative, was " + minEnchantments);
        }
        enchantments = List.copyOf(new LinkedHashSet<>(enchantments == null ? List.of() : enchantments));
        var distinct =
                enchantments.stream().map(Required::enchantment).distinct().count();
        if (distinct != enchantments.size()) {
            throw new IllegalArgumentException("An enchantment may be required only once");
        }
        minEnchantments = Math.max(minEnchantments, enchantments.size());
    }

    /**
     * Criteria accepting any of the given items, without enchantment conditions.
     *
     * @param items materials and item tags
     * @return the criteria
     */
    public static ItemCriteria of(String... items) {
        return new ItemCriteria(List.of(items), 0, List.of());
    }

    /**
     * Criteria like these with another enchantment count.
     *
     * @param minEnchantments the fewest enchantments an item must carry
     * @return a copy with the new count
     */
    public ItemCriteria withMinEnchantments(int minEnchantments) {
        return new ItemCriteria(items, minEnchantments, enchantments);
    }

    /**
     * Criteria like these that also require an enchantment.
     *
     * @param enchantment key of the enchantment, such as {@code minecraft:sharpness}
     * @param minLevel    the lowest level accepted
     * @return a copy that also requires the enchantment
     */
    public ItemCriteria withEnchantment(String enchantment, int minLevel) {
        var required = new ArrayList<>(enchantments);
        required.add(new Required(enchantment, minLevel));
        return new ItemCriteria(items, minEnchantments, required);
    }

    /**
     * Whether other criteria accept the same: the same items and enchantments in any order, and the same count.
     *
     * @param other the other criteria
     * @return true when both accept the same items
     */
    public boolean sameAs(ItemCriteria other) {
        return minEnchantments == other.minEnchantments
                && Set.copyOf(items).equals(Set.copyOf(other.items))
                && Set.copyOf(enchantments).equals(Set.copyOf(other.enchantments));
    }

    /**
     * Whether an entry names a tag.
     *
     * @param item an entry of {@link #items()}
     * @return true for a tag
     */
    public static boolean isTag(String item) {
        return item.startsWith("#");
    }

    /**
     * Whether a material belongs to the set. Unknown materials and tags accept nothing.
     *
     * @param material the material of an item
     * @return true when an entry is the material or a tag containing it
     */
    public boolean accepts(Material material) {
        for (var item : items) {
            if (isTag(item)) {
                var tag = tag(item);
                if (tag.isPresent() && tag.get().isTagged(material)) return true;
            } else if (material.name().equals(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every material the set accepts: the materials in the order they are named, the members of each tag by name.
     * Unknown entries add nothing.
     *
     * @return the materials
     */
    public Set<Material> materials() {
        Set<Material> materials = new LinkedHashSet<>();
        for (var item : items) {
            if (isTag(item)) {
                tag(item).ifPresent(tag -> tag.getValues().stream()
                        .sorted(Comparator.comparing(Material::name))
                        .forEach(materials::add));
            } else {
                Optional.ofNullable(Material.getMaterial(item)).ifPresent(materials::add);
            }
        }
        return materials;
    }

    /**
     * The item standing for the line where one item has to be shown, such as an icon.
     *
     * @return the first material of the set, a barrier when the set accepts nothing
     */
    public ItemStack template() {
        return ItemStack.of(materials().stream().findFirst().orElse(Material.BARRIER));
    }

    /**
     * The item tag an entry names.
     *
     * @param item a tag entry such as {@code #minecraft:logs}
     * @return the tag, empty when the server does not know it
     */
    public static Optional<Tag<Material>> tag(String item) {
        var key = NamespacedKey.fromString(item.substring(1));
        if (key == null) return Optional.empty();
        return Optional.ofNullable(Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class));
    }

    private static String normalize(String item) {
        Objects.requireNonNull(item, "item");
        var trimmed = item.trim();
        if (trimmed.isEmpty() || trimmed.equals("#")) throw new IllegalArgumentException("An item must not be blank");
        if (isTag(trimmed)) return "#" + key(trimmed.substring(1));
        var name = trimmed.toUpperCase(Locale.ROOT);
        return name.startsWith("MINECRAFT:") ? name.substring("MINECRAFT:".length()) : name;
    }

    private static String key(String key) {
        var lower = key.trim().toLowerCase(Locale.ROOT);
        return lower.contains(":") ? lower : "minecraft:" + lower;
    }

    /**
     * An enchantment an item must carry.
     *
     * @param enchantment key of the enchantment, such as {@code minecraft:sharpness}
     * @param minLevel    the lowest level accepted, at least 1
     */
    public record Required(String enchantment, int minLevel) {
        /**
         * Validates and normalizes the key.
         *
         * @throws IllegalArgumentException when the key is blank or the level below 1
         */
        public Required {
            Objects.requireNonNull(enchantment, "enchantment");
            if (enchantment.isBlank()) throw new IllegalArgumentException("The enchantment must not be blank");
            if (minLevel < 1) throw new IllegalArgumentException("The level must be at least 1, was " + minLevel);
            enchantment = key(enchantment);
        }
    }
}
