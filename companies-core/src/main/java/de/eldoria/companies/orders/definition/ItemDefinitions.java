/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.definition;

import de.eldoria.companies.commands.support.CommandFailure;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.DyeColor;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Builds the item of an {@link ItemDefinition}, refusing what survival cannot make, so every order defined on the
 * dashboard can be delivered: an enchantment the material cannot take or above its highest level, two enchantments
 * that exclude each other, a potion type on something that is no potion or tipped arrow, a dye on something that is not
 * dyeable leather, a trim on something that is no armour.
 */
public final class ItemDefinitions {
    private ItemDefinitions() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Builds the item of a definition, with an amount of one.
     *
     * @param definition the definition
     * @return the item
     * @throws CommandFailure when the definition is no item survival can make
     */
    public static ItemStack build(ItemDefinition definition) {
        var material = material(definition.material());
        var stack = new ItemStack(material);
        enchant(stack, definition);
        var potion = definition.potion();
        if (potion != null) potion(stack, potion);
        var dye = definition.dye();
        if (dye != null) dye(stack, dye);
        var trim = definition.trim();
        if (trim != null) trim(stack, trim);
        return stack;
    }

    /**
     * @param key a material key
     * @return the material, when it is an item
     * @throws CommandFailure when it names no item
     */
    public static Material material(String key) {
        var material = Material.matchMaterial(key);
        if (material == null || !material.isItem() || material.isAir()) throw refused("dashboard.item.unknownMaterial");
        return material;
    }

    private static void enchant(ItemStack stack, ItemDefinition definition) {
        if (definition.enchantments().isEmpty()) return;
        var book = stack.getType() == Material.ENCHANTED_BOOK;
        List<Enchantment> chosen = new ArrayList<>();
        for (var entry : definition.enchantments().entrySet()) {
            var enchantment = lookup(RegistryKey.ENCHANTMENT, entry.getKey())
                    .orElseThrow(() -> refused("dashboard.item.unknownEnchantment"));
            var level = entry.getValue() == null ? 0 : entry.getValue();
            if (level < 1 || level > enchantment.getMaxLevel()) throw refused("dashboard.item.enchantmentLevel");
            if (!book && !enchantment.canEnchantItem(new ItemStack(stack.getType()))) {
                throw refused("dashboard.item.enchantmentNotApplicable");
            }
            if (chosen.stream().anyMatch(other -> other.conflictsWith(enchantment))) {
                throw refused("dashboard.item.enchantmentConflict");
            }
            chosen.add(enchantment);
            if (book) {
                stack.editMeta(EnchantmentStorageMeta.class, meta -> meta.addStoredEnchant(enchantment, level, false));
            } else {
                stack.addEnchantment(enchantment, level);
            }
        }
    }

    private static void potion(ItemStack stack, String key) {
        var type = lookup(RegistryKey.POTION, key).orElseThrow(() -> refused("dashboard.item.unknownPotion"));
        if (!(stack.getItemMeta() instanceof PotionMeta)) throw refused("dashboard.item.potionNotApplicable");
        stack.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(type));
    }

    private static void dye(ItemStack stack, String name) {
        DyeColor color;
        try {
            color = DyeColor.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw refused("dashboard.item.unknownDye");
        }
        if (!(stack.getItemMeta() instanceof LeatherArmorMeta)) throw refused("dashboard.item.dyeNotApplicable");
        stack.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(color.getColor()));
    }

    private static void trim(ItemStack stack, ItemDefinition.Trim trim) {
        var pattern = lookup(RegistryKey.TRIM_PATTERN, trim.pattern())
                .orElseThrow(() -> refused("dashboard.item.unknownTrim"));
        var material = lookup(RegistryKey.TRIM_MATERIAL, trim.material())
                .orElseThrow(() -> refused("dashboard.item.unknownTrim"));
        if (!(stack.getItemMeta() instanceof ArmorMeta)) throw refused("dashboard.item.trimNotApplicable");
        stack.editMeta(ArmorMeta.class, meta -> meta.setTrim(new ArmorTrim(material, pattern)));
    }

    /**
     * Looks an entry of a registry up by its key.
     *
     * @param registry the registry
     * @param key      the key, with or without the {@code minecraft:} namespace
     * @param <T>      the entry type
     * @return the entry, empty for an unknown key
     */
    public static <T extends Keyed> Optional<T> lookup(RegistryKey<T> registry, String key) {
        var namespaced = NamespacedKey.fromString(key.toLowerCase(Locale.ROOT));
        if (namespaced == null) return Optional.empty();
        Registry<T> entries = RegistryAccess.registryAccess().getRegistry(registry);
        return Optional.ofNullable(entries.get(namespaced));
    }

    private static CommandFailure refused(String key) {
        return CommandFailure.of(key);
    }

    /**
     * @return the dye colours a leather item can take from one dye
     */
    public static List<DyeColor> dyes() {
        return List.of(DyeColor.values());
    }
}
