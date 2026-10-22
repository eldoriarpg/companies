/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.configuration.elements.ItemMatchSettings;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

@SuppressWarnings("UnstableApiUsage")
class ItemMatcherTest {
    private static final List<String> DEFAULT_IGNORED =
            List.of("minecraft:custom_name", "minecraft:repair_cost", "minecraft:lore");

    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    private static ItemMatcher matcher(boolean allowDamaged) {
        return ItemMatcher.of(new ItemMatchSettings(DEFAULT_IGNORED, allowDamaged), Logger.getAnonymousLogger());
    }

    private static ComponentStack plain(Material material) {
        return new ComponentStack(material);
    }

    private static ComponentStack enchanted(Material material, int sharpness) {
        var stack = plain(material);
        stack.setData(
                DataComponentTypes.ENCHANTMENTS,
                ItemEnchantments.itemEnchantments()
                        .add(Enchantment.SHARPNESS, sharpness)
                        .build());
        return stack;
    }

    private static ComponentStack book(int sharpness) {
        var stack = plain(Material.ENCHANTED_BOOK);
        stack.setData(
                DataComponentTypes.STORED_ENCHANTMENTS,
                ItemEnchantments.itemEnchantments()
                        .add(Enchantment.SHARPNESS, sharpness)
                        .build());
        return stack;
    }

    private static ComponentStack damaged(int damage) {
        var stack = plain(Material.DIAMOND_SWORD);
        stack.setData(DataComponentTypes.MAX_DAMAGE, 100);
        stack.setData(DataComponentTypes.DAMAGE, damage);
        return stack;
    }

    @Test
    void stacksCompareTheirComponents() {
        Assertions.assertTrue(enchanted(Material.DIAMOND_SWORD, 5)
                .matchesWithoutData(enchanted(Material.DIAMOND_SWORD, 5), Set.of(), true));
        Assertions.assertFalse(enchanted(Material.DIAMOND_SWORD, 5)
                .matchesWithoutData(enchanted(Material.DIAMOND_SWORD, 4), Set.of(), true));
        Assertions.assertTrue(enchanted(Material.DIAMOND_SWORD, 5).clone().hasData(DataComponentTypes.ENCHANTMENTS));
    }

    @Test
    void plainMatchesPlain() {
        var matcher = matcher(false);

        Assertions.assertTrue(matcher.matches(plain(Material.STONE), plain(Material.STONE)));
        Assertions.assertEquals(0, matcher.extras(plain(Material.STONE), plain(Material.STONE)));
    }

    @Test
    void otherTypesDoNotMatch() {
        Assertions.assertFalse(matcher(false).matches(plain(Material.STONE), plain(Material.DIRT)));
    }

    @Test
    void enchantedMatchesMaterialLineWithOneExtra() {
        var matcher = matcher(false);
        var offered = enchanted(Material.DIAMOND_SWORD, 5);

        Assertions.assertTrue(matcher.matches(plain(Material.DIAMOND_SWORD), offered));
        Assertions.assertEquals(1, matcher.extras(plain(Material.DIAMOND_SWORD), offered));
    }

    @Test
    void damageIsAShareOfMaxDamage() {
        var matcher = matcher(false);

        Assertions.assertEquals(0.25, matcher.damage(damaged(25)), 0.0001);
        Assertions.assertEquals(0.0, matcher.damage(plain(Material.STONE)), 0.0001);
    }

    @Test
    void exactBookRequiresTheSameEnchantment() {
        var matcher = matcher(false);

        Assertions.assertFalse(matcher.matches(book(5), book(4)));
        Assertions.assertTrue(matcher.matches(book(5), book(5)));
        Assertions.assertFalse(matcher.matches(book(5), plain(Material.ENCHANTED_BOOK)));
    }

    @Test
    void exactBookIgnoresCustomNameAndLore() {
        var matcher = matcher(false);
        var offered = book(5);
        offered.setData(DataComponentTypes.CUSTOM_NAME, Component.text("Signed"));
        offered.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(Component.text("by a worker"))));

        Assertions.assertTrue(matcher.matches(book(5), offered));
        Assertions.assertEquals(0, matcher.extras(book(5), offered));
    }

    @Test
    void damagedItemsNeedAllowDamaged() {
        var requested = plain(Material.DIAMOND_SWORD);
        requested.setData(DataComponentTypes.MAX_DAMAGE, 100);

        Assertions.assertFalse(matcher(false).matches(requested, damaged(10)));
        Assertions.assertTrue(matcher(false).matches(requested, damaged(0)));
        Assertions.assertTrue(matcher(true).matches(requested, damaged(10)));
        Assertions.assertEquals(0, matcher(true).extras(requested, damaged(10)));
    }

    @Test
    void filledContainersAreRejected() {
        var matcher = matcher(false);
        var full = plain(Material.SHULKER_BOX);
        full.setData(
                DataComponentTypes.CONTAINER,
                ItemContainerContents.containerContents(List.of(ItemStack.of(Material.STONE))));
        var empty = plain(Material.SHULKER_BOX);
        empty.setData(DataComponentTypes.CONTAINER, ItemContainerContents.containerContents(List.of()));
        var bundle = plain(Material.BUNDLE);
        bundle.setData(
                DataComponentTypes.BUNDLE_CONTENTS,
                BundleContents.bundleContents(List.of(ItemStack.of(Material.DIRT))));

        Assertions.assertFalse(matcher.matches(plain(Material.SHULKER_BOX), full));
        Assertions.assertTrue(matcher.matches(plain(Material.SHULKER_BOX), empty));
        Assertions.assertFalse(matcher.matches(plain(Material.BUNDLE), bundle));
        Assertions.assertTrue(matcher.template(full).isEmpty());
        Assertions.assertTrue(matcher.template(bundle).isEmpty());
    }

    @Test
    void templateStripsIgnoredDataAndDamage() {
        var matcher = matcher(true);
        var hand = enchanted(Material.DIAMOND_SWORD, 5);
        hand.setAmount(3);
        hand.setData(DataComponentTypes.CUSTOM_NAME, Component.text("Owner's sword"));
        hand.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(Component.text("Owner"))));
        hand.setData(DataComponentTypes.DAMAGE, 12);
        hand.setData(DataComponentTypes.REPAIR_COST, 3);

        var template = matcher.template(hand).orElseThrow();

        Assertions.assertEquals(1, template.getAmount());
        Assertions.assertEquals(Set.of(DataComponentTypes.ENCHANTMENTS), template.getDataTypes());
        Assertions.assertTrue(matcher.isExact(template));
        Assertions.assertEquals(3, hand.getAmount());
        Assertions.assertTrue(hand.hasData(DataComponentTypes.CUSTOM_NAME));
        Assertions.assertFalse(
                matcher.isExact(matcher.template(plain(Material.STONE)).orElseThrow()));
    }

    @Test
    void unknownIgnoredKeysAreLoggedAndSkipped() {
        List<LogRecord> records = new ArrayList<>();
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });

        var matcher = ItemMatcher.of(
                new ItemMatchSettings(
                        List.of("minecraft:not_a_component", "Not A Key", "minecraft:custom_name"), false),
                logger);

        Assertions.assertEquals(2, records.size());
        var named = book(5);
        named.setData(DataComponentTypes.CUSTOM_NAME, Component.text("Signed"));
        Assertions.assertEquals(0, matcher.extras(book(5), named));
    }
}
