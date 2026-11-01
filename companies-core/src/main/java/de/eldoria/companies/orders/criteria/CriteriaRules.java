/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.criteria;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.order.ItemCriteria;
import de.eldoria.eldoutilities.messages.Replacement;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Checks criteria before an order with them is created.
 * <p>
 * Every material has to be a known item, every tag a known item tag, and the set must accept at least one item. Every
 * required enchantment has to be known, its level within the enchantment's maximum, and some item of the set has to
 * be able to carry it; two required enchantments may not conflict. Some single item of the set has to be able to carry
 * all required enchantments together, and as many enchantments as the count asks for without any two conflicting.
 * <p>
 * A potion condition has to name a known potion type, and some item of the set has to hold potions. A durability
 * condition needs some item of the set that has durability.
 */
public final class CriteriaRules {
    private CriteriaRules() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Finds what makes criteria unusable.
     *
     * @param criteria the criteria
     * @return the first problem, empty when the criteria are fine
     */
    public static Optional<CommandFailure> problem(ItemCriteria criteria) {
        for (var item : criteria.items()) {
            if (ItemCriteria.isTag(item)) {
                if (ItemCriteria.tag(item).isEmpty()) return refused("order.criteria.error.unknownTag", item);
                continue;
            }
            var material = Material.getMaterial(item);
            if (material == null || material.isAir() || !material.isItem()) {
                return refused("order.criteria.error.unknownItem", item);
            }
        }
        var materials = criteria.materials().stream()
                .filter(material -> !material.isAir() && material.isItem())
                .toList();
        if (materials.isEmpty()) return refused("order.criteria.error.empty", String.join(", ", criteria.items()));
        var potion = criteria.potion();
        if (potion != null) {
            if (criteria.potionType().isEmpty()) return refused("order.criteria.error.unknownPotion", potion);
            if (materials.stream().noneMatch(CriteriaRules::holdsPotion)) {
                return refused("order.criteria.error.potionNotApplicable", potion);
            }
        }
        if (criteria.minDurability() > 0 && materials.stream().noneMatch(material -> material.getMaxDurability() > 0)) {
            return refused("order.criteria.error.durabilityNotApplicable", criteria.minDurability() + " %");
        }
        List<Enchantment> required = new ArrayList<>();
        for (var entry : criteria.enchantments()) {
            var enchantment = entry.lookup();
            if (enchantment.isEmpty()) return refused("order.criteria.error.unknownEnchantment", entry.enchantment());
            if (entry.minLevel() > enchantment.get().getMaxLevel()) {
                return refused("order.criteria.error.enchantmentLevel", entry.enchantment());
            }
            if (!applicable(enchantment.get(), materials)) {
                return refused("order.criteria.error.enchantmentNotApplicable", entry.enchantment());
            }
            for (var other : required) {
                if (conflict(other, enchantment.get())) {
                    return refused("order.criteria.error.enchantmentConflict", entry.enchantment());
                }
            }
            required.add(enchantment.get());
        }
        var fitting = materials.stream()
                .filter(material -> required.stream().allMatch(enchantment -> carries(material, enchantment)))
                .toList();
        if (fitting.isEmpty()) {
            return refused(
                    "order.criteria.error.enchantmentsApart",
                    criteria.enchantments().stream()
                            .map(ItemCriteria.Required::enchantment)
                            .collect(Collectors.joining(", ")));
        }
        if (!carriesCount(fitting, required, criteria.minEnchantments())) {
            return refused("order.criteria.error.enchantmentCount", String.valueOf(criteria.minEnchantments()));
        }
        return Optional.empty();
    }

    private static boolean applicable(Enchantment enchantment, List<Material> materials) {
        return materials.stream().anyMatch(material -> carries(material, enchantment));
    }

    private static boolean carries(Material material, Enchantment enchantment) {
        return holdsAny(material) || enchantment.canEnchantItem(new ItemStack(material));
    }

    private static boolean carriesCount(List<Material> materials, List<Enchantment> required, int count) {
        var needed = count - required.size();
        if (needed <= 0) return true;
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        for (var material : materials) {
            List<Enchantment> candidates = new ArrayList<>();
            for (var enchantment : registry) {
                if (required.contains(enchantment) || !carries(material, enchantment)) continue;
                if (required.stream().anyMatch(other -> conflict(other, enchantment))) continue;
                candidates.add(enchantment);
            }
            if (compatible(candidates, needed)) return true;
        }
        return false;
    }

    /**
     * Whether a number of the enchantments can go on one item together, no two of them conflicting.
     *
     * @param enchantments the enchantments to choose from
     * @param needed       how many are needed
     * @return true when that many fit together; with too many enchantments to search, whether there are enough
     */
    private static boolean compatible(List<Enchantment> enchantments, int needed) {
        var count = enchantments.size();
        if (count < needed) return false;
        if (count >= Long.SIZE) return true;
        var conflicts = new long[count];
        for (var first = 0; first < count; first++) {
            for (var second = 0; second < count; second++) {
                if (first != second && conflict(enchantments.get(first), enchantments.get(second))) {
                    conflicts[first] |= 1L << second;
                }
            }
        }
        return fits(-1L >>> (Long.SIZE - count), conflicts, needed);
    }

    private static boolean fits(long open, long[] conflicts, int needed) {
        if (needed <= 0) return true;
        if (Long.bitCount(open) < needed) return false;
        var first = Long.numberOfTrailingZeros(open);
        var rest = open & ~(1L << first);
        var excluded = conflicts[first] & rest;
        if (fits(rest & ~excluded, conflicts, needed - 1)) return true;
        return excluded != 0 && fits(rest, conflicts, needed);
    }

    private static boolean conflict(Enchantment first, Enchantment second) {
        return first.conflictsWith(second) || second.conflictsWith(first);
    }

    /**
     * Whether a material holds a potion type: potions, splash and lingering potions and tipped arrows.
     *
     * @param material the material
     * @return true when it holds one
     */
    public static boolean holdsPotion(Material material) {
        return new ItemStack(material).getItemMeta() instanceof PotionMeta;
    }

    private static boolean holdsAny(Material material) {
        return material == Material.ENCHANTED_BOOK;
    }

    private static Optional<CommandFailure> refused(String key, String value) {
        return Optional.of(CommandFailure.of(key, Replacement.create("entry", value)));
    }
}
