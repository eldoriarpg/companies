/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.criteria;

import de.eldoria.companies.components.order.ItemCriteria;
import de.eldoria.companies.dashboard.views.PlainText;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The one text form of criteria, for players in game and as plain text for the dashboard and Discord:
 * {@code Logs / Bamboo Block (✦ ≥3, Sharpness III+)}.
 * <p>
 * It needs no translations of its own. Materials, tags and enchantments use the client's translations in game and
 * their English names as plain text; the conditions are symbols. At most {@value #SHOWN} items are named, the rest
 * are counted.
 */
public final class CriteriaText {
    /**
     * The most items named before the rest are counted.
     */
    public static final int SHOWN = 3;

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private CriteriaText() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * The criteria for players in game, translated by their client.
     *
     * @param criteria the criteria
     * @return the text
     */
    public static Component component(ItemCriteria criteria) {
        List<Component> items = new ArrayList<>();
        var named = criteria.items().stream().limit(SHOWN).toList();
        for (var item : named) items.add(itemComponent(item));
        var more = criteria.items().size() - named.size();
        if (more > 0) items.add(Component.text("+" + more));
        var text = Component.join(JoinConfiguration.separator(Component.text(" / ")), items);
        List<Component> conditions = new ArrayList<>();
        if (criteria.minEnchantments() > criteria.enchantments().size()) {
            conditions.add(Component.text("✦ ≥" + criteria.minEnchantments()));
        }
        for (var required : criteria.enchantments()) conditions.add(requiredComponent(required));
        if (conditions.isEmpty()) return text;
        return text.append(Component.text(" ("))
                .append(Component.join(JoinConfiguration.separator(Component.text(", ")), conditions))
                .append(Component.text(")"));
    }

    /**
     * The criteria as plain English text.
     *
     * @param criteria the criteria
     * @return the text
     */
    public static String plain(ItemCriteria criteria) {
        List<String> items = new ArrayList<>();
        var named = criteria.items().stream().limit(SHOWN).toList();
        for (var item : named) items.add(itemName(item));
        var more = criteria.items().size() - named.size();
        if (more > 0) items.add("+" + more);
        var text = new StringBuilder(String.join(" / ", items));
        List<String> conditions = new ArrayList<>();
        if (criteria.minEnchantments() > criteria.enchantments().size()) {
            conditions.add("✦ ≥" + criteria.minEnchantments());
        }
        for (var required : criteria.enchantments()) conditions.add(requiredName(required));
        if (!conditions.isEmpty())
            text.append(" (").append(String.join(", ", conditions)).append(')');
        return text.toString();
    }

    /**
     * The English name of an item entry: the material name, or {@code #} and the tag's name.
     *
     * @param item a material or tag entry
     * @return the name
     */
    public static String itemName(String item) {
        if (ItemCriteria.isTag(item)) {
            var key = NamespacedKey.fromString(item.substring(1));
            return "#" + (key == null ? item.substring(1) : PlainText.keyName(key));
        }
        var material = Material.getMaterial(item);
        return material == null ? item : PlainText.materialName(material);
    }

    /**
     * A level as Roman numeral, as the game shows it.
     *
     * @param level the level
     * @return the numeral, the number itself above ten
     */
    public static String roman(int level) {
        return level > 0 && level < ROMAN.length ? ROMAN[level] : String.valueOf(level);
    }

    private static Component itemComponent(String item) {
        if (ItemCriteria.isTag(item)) {
            var key = item.substring(1).replace(':', '.').replace('/', '.');
            return Component.translatable("tag.item." + key, itemName(item));
        }
        var material = Material.getMaterial(item);
        return material == null ? Component.text(item) : Component.translatable(material);
    }

    private static Component requiredComponent(ItemCriteria.Required required) {
        var name = enchantment(required.enchantment())
                .map(Enchantment::description)
                .orElseGet(() -> Component.text(enchantmentName(required.enchantment())));
        if (required.minLevel() <= 1) return name;
        return name.append(Component.text(" "))
                .append(Component.translatable("enchantment.level." + required.minLevel(), roman(required.minLevel())))
                .append(Component.text("+"));
    }

    private static String requiredName(ItemCriteria.Required required) {
        var name = enchantmentName(required.enchantment());
        if (required.minLevel() <= 1) return name;
        return name + " " + roman(required.minLevel()) + "+";
    }

    /**
     * The English name of an enchantment, made from its key.
     *
     * @param enchantment the key, such as {@code minecraft:fire_aspect}
     * @return the name, such as {@code Fire Aspect}
     */
    public static String enchantmentName(String enchantment) {
        var key = NamespacedKey.fromString(enchantment);
        return key == null ? enchantment : PlainText.keyName(key);
    }

    private static Optional<Enchantment> enchantment(String enchantment) {
        var key = NamespacedKey.fromString(enchantment);
        if (key == null) return Optional.empty();
        try {
            return Optional.ofNullable(RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.ENCHANTMENT)
                    .get(key));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
