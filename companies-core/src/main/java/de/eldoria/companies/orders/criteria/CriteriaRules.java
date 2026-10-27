/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.criteria;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.order.ItemCriteria;
import de.eldoria.companies.orders.definition.ItemDefinitions;
import de.eldoria.eldoutilities.messages.Replacement;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

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
        List<Enchantment> required = new ArrayList<>();
        for (var entry : criteria.enchantments()) {
            var enchantment = ItemDefinitions.lookup(RegistryKey.ENCHANTMENT, entry.enchantment());
            if (enchantment.isEmpty()) return refused("order.criteria.error.unknownEnchantment", entry.enchantment());
            if (entry.minLevel() > enchantment.get().getMaxLevel()) {
                return refused("order.criteria.error.enchantmentLevel", entry.enchantment());
            }
            if (!applicable(enchantment.get(), materials)) {
                return refused("order.criteria.error.enchantmentNotApplicable", entry.enchantment());
            }
            for (var other : required) {
                if (other.conflictsWith(enchantment.get())) {
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
        if (criteria.minEnchantments() > 0 && criteria.minEnchantments() > mostEnchantments(fitting, required)) {
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

    private static int mostEnchantments(List<Material> materials, List<Enchantment> required) {
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        var most = 0;
        for (var material : materials) {
            List<Enchantment> candidates = new ArrayList<>();
            for (var enchantment : registry) {
                if (required.contains(enchantment) || !carries(material, enchantment)) continue;
                if (required.stream().anyMatch(other -> other.conflictsWith(enchantment))) continue;
                candidates.add(enchantment);
            }
            most = Math.max(most, required.size() + compatible(candidates));
        }
        return most;
    }

    /**
     * The size of the largest group of enchantments of which no two conflict.
     *
     * @param enchantments the enchantments to choose from
     * @return the size of the group, the number of enchantments when there are too many to search
     */
    private static int compatible(List<Enchantment> enchantments) {
        var count = enchantments.size();
        if (count >= Long.SIZE) return count;
        var conflicts = new long[count];
        for (var first = 0; first < count; first++) {
            for (var second = 0; second < count; second++) {
                if (first != second && enchantments.get(first).conflictsWith(enchantments.get(second))) {
                    conflicts[first] |= 1L << second;
                }
            }
        }
        return largest(count == 0 ? 0 : -1L >>> (Long.SIZE - count), conflicts);
    }

    private static int largest(long open, long[] conflicts) {
        if (open == 0) return 0;
        var first = Long.numberOfTrailingZeros(open);
        var rest = open & ~(1L << first);
        var excluded = conflicts[first] & rest;
        var with = 1 + largest(rest & ~excluded, conflicts);
        if (excluded == 0) return with;
        return Math.max(with, largest(rest, conflicts));
    }

    private static boolean holdsAny(Material material) {
        return material == Material.ENCHANTED_BOOK;
    }

    private static Optional<CommandFailure> refused(String key, String value) {
        return Optional.of(CommandFailure.of(key, Replacement.create("entry", value)));
    }
}
