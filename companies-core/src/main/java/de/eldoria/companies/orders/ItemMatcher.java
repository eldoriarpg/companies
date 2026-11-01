/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.order.IOrderContent;
import de.eldoria.companies.components.order.ItemCriteria;
import de.eldoria.companies.configuration.elements.ItemMatchSettings;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Decides which items may be delivered to an order line and turns items into line templates.
 * <p>
 * An offered item matches a requested item when it has the same type, holds no container or bundle content, is
 * undamaged unless damaged items are allowed, and carries every component the request overrides with the same value.
 * Components the offer adds on top of the request, ignored components and damage never decide a match. An offer that
 * removes a component the request keeps does not match. Criteria lines match by their criteria instead, see
 * {@link #matches(ItemStack, ItemCriteria, ItemStack)}.
 * <p>
 * The matcher is immutable and built from the settings on load and on reload.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ItemMatcher {
    private static final Set<DataComponentType> ENCHANTMENT_TYPES =
            Set.of(DataComponentTypes.ENCHANTMENTS, DataComponentTypes.STORED_ENCHANTMENTS);

    private final Set<DataComponentType> ignored;
    private final boolean allowDamaged;

    private ItemMatcher(Set<DataComponentType> ignored, boolean allowDamaged) {
        this.ignored = Set.copyOf(ignored);
        this.allowDamaged = allowDamaged;
    }

    /**
     * Builds a matcher and logs unknown component keys to the server log.
     *
     * @param settings matching settings
     * @return matcher
     */
    public static ItemMatcher of(ItemMatchSettings settings) {
        return of(settings, Bukkit.getLogger());
    }

    /**
     * Builds a matcher. Keys which are no data component type are logged once and skipped.
     *
     * @param settings matching settings
     * @param logger   logger for unknown keys
     * @return matcher
     */
    public static ItemMatcher of(ItemMatchSettings settings, Logger logger) {
        Set<DataComponentType> ignored = new HashSet<>();
        for (var key : settings.ignoredComponents()) {
            var type = componentType(key);
            if (type.isEmpty()) {
                logger.warning("Unknown data component type in matching.ignoredComponents: " + key);
                continue;
            }
            ignored.add(type.get());
        }
        return new ItemMatcher(ignored, settings.allowDamaged());
    }

    private static Optional<DataComponentType> componentType(String key) {
        var namespacedKey = NamespacedKey.fromString(key);
        if (namespacedKey == null) return Optional.empty();
        return Optional.ofNullable(Registry.DATA_COMPONENT_TYPE.get(namespacedKey));
    }

    /**
     * Checks whether an offered item may be delivered for a requested item.
     *
     * @param requested template of the order line
     * @param offered   item offered by a worker
     * @return true when the offer satisfies the request
     */
    public boolean matches(ItemStack requested, ItemStack offered) {
        if (offered.getType() != requested.getType()) return false;
        if (hasContent(offered)) return false;
        if (!allowDamaged && isDamaged(offered)) return false;
        Set<DataComponentType> excluded = new HashSet<>(ignored);
        excluded.add(DataComponentTypes.DAMAGE);
        excluded.addAll(added(requested, offered));
        return requested.matchesWithoutData(offered, excluded, true);
    }

    /**
     * Checks whether an offered item may be delivered for a line of any kind.
     *
     * @param line    the order line
     * @param offered item offered by a worker
     * @return true when the offer satisfies the line
     */
    public boolean matches(IOrderContent line, ItemStack offered) {
        return matches(line.stack(), line.criteria().orElse(null), offered);
    }

    /**
     * Checks whether an offered item may be delivered for a request.
     * <p>
     * Without criteria the template decides, see {@link #matches(ItemStack, ItemStack)}. With criteria the item's
     * material has to be in their set and the item has to meet their enchantment and potion conditions; other data
     * does not matter. Items with container content never match. A durability condition decides about damaged items
     * for its line; without one, damaged items match only when allowed.
     *
     * @param template template of the order line
     * @param criteria criteria of a criteria line, null for material and exact lines
     * @param offered  item offered by a worker
     * @return true when the offer satisfies the request
     */
    public boolean matches(ItemStack template, @Nullable ItemCriteria criteria, ItemStack offered) {
        if (criteria == null) return matches(template, offered);
        if (!criteria.accepts(offered.getType())) return false;
        if (hasContent(offered)) return false;
        if (criteria.minDurability() > 0) {
            if (!durable(offered, criteria.minDurability())) return false;
        } else if (!allowDamaged && isDamaged(offered)) {
            return false;
        }
        var potion = criteria.potion();
        if (potion != null && !potion.equals(potion(offered))) return false;
        return meets(criteria, enchantments(offered));
    }

    private static boolean customPotion(ItemStack stack) {
        var contents = stack.getData(DataComponentTypes.POTION_CONTENTS);
        return contents != null
                && (!contents.customEffects().isEmpty()
                        || contents.customColor() != null
                        || contents.customName() != null);
    }

    /**
     * The potion type an item holds.
     *
     * @param stack the item
     * @return the key of its potion type, such as {@code minecraft:swiftness}; null without one
     */
    public static @Nullable String potion(ItemStack stack) {
        var contents = stack.getData(DataComponentTypes.POTION_CONTENTS);
        var type = contents == null ? null : contents.potion();
        return type == null ? null : type.getKey().toString();
    }

    /**
     * Counts what an offer brings beyond a request: the extra components for a template; for criteria the enchantments
     * beyond the required count and every other component the item carries, leaving out ignored components, damage
     * and the potion a potion condition asks for, unless the potion also carries custom effects, a colour or a name.
     *
     * @param template template of the order line
     * @param criteria criteria of a criteria line, null for material and exact lines
     * @param offered  item offered by a worker
     * @return number of extras
     */
    public int extras(ItemStack template, @Nullable ItemCriteria criteria, ItemStack offered) {
        if (criteria == null) return extras(template, offered);
        var components = (int) offered.getDataTypes().stream()
                .filter(offered::isDataOverridden)
                .filter(type -> !ignored.contains(type))
                .filter(type -> !ENCHANTMENT_TYPES.contains(type) && !type.equals(DataComponentTypes.DAMAGE))
                .filter(type -> criteria.potion() == null
                        || !type.equals(DataComponentTypes.POTION_CONTENTS)
                        || customPotion(offered))
                .count();
        return components + Math.max(0, enchantments(offered).size() - criteria.minEnchantments());
    }

    /**
     * The enchantments an item carries, with those an enchanted book stores.
     *
     * @param stack the item
     * @return level per enchantment key, such as {@code minecraft:sharpness}
     */
    public static Map<String, Integer> enchantments(ItemStack stack) {
        Map<String, Integer> levels = new HashMap<>();
        for (var type : List.of(DataComponentTypes.ENCHANTMENTS, DataComponentTypes.STORED_ENCHANTMENTS)) {
            var enchantments = stack.getData(type);
            if (enchantments == null) continue;
            enchantments
                    .enchantments()
                    .forEach((enchantment, level) ->
                            levels.merge(enchantment.getKey().toString(), level, Math::max));
        }
        return levels;
    }

    private static boolean meets(ItemCriteria criteria, Map<String, Integer> enchantments) {
        if (enchantments.size() < criteria.minEnchantments()) return false;
        for (var required : criteria.enchantments()) {
            if (enchantments.getOrDefault(required.enchantment(), 0) < required.minLevel()) return false;
        }
        return true;
    }

    /**
     * Counts the components an offer adds on top of the request, leaving out ignored components and damage.
     * <p>
     * Deliveries take the items with the fewest extras first, so plain items are delivered before enchanted ones.
     *
     * @param requested template of the order line
     * @param offered   item offered by a worker
     * @return number of extra components
     */
    public int extras(ItemStack requested, ItemStack offered) {
        return (int) added(requested, offered).stream()
                .filter(type -> !ignored.contains(type))
                .filter(type -> !type.equals(DataComponentTypes.DAMAGE))
                .count();
    }

    /**
     * Turns an item into the template of an order line.
     * <p>
     * The template has an amount of one and neither ignored components nor damage or repair cost, which also keeps
     * the owner's name out of custom names and lore.
     *
     * @param hand item to order
     * @return the template, or empty when the item holds container or bundle content and can not be ordered
     */
    public Optional<ItemStack> template(ItemStack hand) {
        if (hasContent(hand)) return Optional.empty();
        var template = hand.clone();
        template.setAmount(1);
        for (var type : ignored) {
            template.resetData(type);
        }
        template.resetData(DataComponentTypes.DAMAGE);
        template.resetData(DataComponentTypes.REPAIR_COST);
        return Optional.of(template);
    }

    /**
     * Checks whether a template requests more than its material.
     *
     * @param template template of an order line
     * @return true when any component of the template differs from its item type
     */
    public boolean isExact(ItemStack template) {
        return template.getDataTypes().stream().anyMatch(template::isDataOverridden);
    }

    /**
     * The damage of an item as a share of its max damage.
     *
     * @param offered item to check
     * @return a value from 0 for undamaged to 1 for broken, 0 when the item can not be damaged
     */
    public double damage(ItemStack offered) {
        var maxDamage = offered.getData(DataComponentTypes.MAX_DAMAGE);
        if (maxDamage == null || maxDamage <= 0) return 0;
        return Math.min(1.0, Math.max(0, damageOf(offered)) / (double) maxDamage);
    }

    /**
     * Whether damaged items may be delivered.
     *
     * @return true when damaged items are accepted
     */
    public boolean allowDamaged() {
        return allowDamaged;
    }

    private static Set<DataComponentType> added(ItemStack requested, ItemStack offered) {
        return offered.getDataTypes().stream()
                .filter(offered::isDataOverridden)
                .filter(type -> !requested.isDataOverridden(type))
                .collect(Collectors.toSet());
    }

    /**
     * Whether an item has at least a share of its durability left. Items without durability always have.
     *
     * @param offered the item
     * @param percent the share, in percent
     * @return true when enough is left
     */
    private static boolean durable(ItemStack offered, int percent) {
        var maxDamage = offered.getData(DataComponentTypes.MAX_DAMAGE);
        if (maxDamage == null || maxDamage <= 0) return true;
        long left = maxDamage - Math.max(0, damageOf(offered));
        return left * 100 >= (long) percent * maxDamage;
    }

    private static boolean isDamaged(ItemStack offered) {
        return damageOf(offered) > 0;
    }

    private static int damageOf(ItemStack stack) {
        var damage = stack.getData(DataComponentTypes.DAMAGE);
        return damage == null ? 0 : damage;
    }

    private static boolean hasContent(ItemStack stack) {
        var container = stack.getData(DataComponentTypes.CONTAINER);
        if (container != null && container.contents().stream().anyMatch(item -> !item.isEmpty())) return true;
        var bundle = stack.getData(DataComponentTypes.BUNDLE_CONTENTS);
        return bundle != null && bundle.contents().stream().anyMatch(item -> !item.isEmpty());
    }
}
