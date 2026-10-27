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

/**
 * Checks criteria before an order with them is created.
 * <p>
 * Every material has to be a known item, every tag a known item tag, and the set must accept at least one item. Every
 * required enchantment has to be known, its level within the enchantment's maximum, and some item of the set has to
 * be able to carry it; two required enchantments may not conflict. The count may not exceed the number of
 * enchantments any item of the set can carry, leaving conflicts aside.
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
        if (criteria.minEnchantments() > maxEnchantments(materials)) {
            return refused("order.criteria.error.enchantmentCount", String.valueOf(criteria.minEnchantments()));
        }
        return Optional.empty();
    }

    private static boolean applicable(Enchantment enchantment, List<Material> materials) {
        for (var material : materials) {
            if (holdsAny(material) || enchantment.canEnchantItem(new ItemStack(material))) return true;
        }
        return false;
    }

    private static int maxEnchantments(List<Material> materials) {
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        var most = 0;
        for (var material : materials) {
            var count = 0;
            for (var enchantment : registry) {
                if (holdsAny(material) || enchantment.canEnchantItem(new ItemStack(material))) count++;
            }
            most = Math.max(most, count);
        }
        return most;
    }

    private static boolean holdsAny(Material material) {
        return material == Material.ENCHANTED_BOOK;
    }

    private static Optional<CommandFailure> refused(String key, String value) {
        return Optional.of(CommandFailure.of(key, Replacement.create("entry", value)));
    }
}
