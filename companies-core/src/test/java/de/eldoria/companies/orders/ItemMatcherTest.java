/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.order.ItemCriteria;
import de.eldoria.companies.configuration.elements.ItemMatchSettings;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.PotionContents;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private static ComponentStack enchanted(Material material, Map<Enchantment, Integer> levels) {
        var stack = plain(material);
        var enchantments = ItemEnchantments.itemEnchantments();
        levels.forEach(enchantments::add);
        stack.setData(DataComponentTypes.ENCHANTMENTS, enchantments.build());
        return stack;
    }

    @Test
    void criteriaAcceptEveryItemOfTheirSet() {
        var matcher = matcher(false);
        var logs = ItemCriteria.of("#minecraft:logs", "BAMBOO_BLOCK");
        var template = logs.template();

        Assertions.assertTrue(matcher.matches(template, logs, plain(Material.OAK_LOG)));
        Assertions.assertTrue(matcher.matches(template, logs, plain(Material.BIRCH_LOG)));
        Assertions.assertTrue(matcher.matches(template, logs, plain(Material.BAMBOO_BLOCK)));
        Assertions.assertFalse(matcher.matches(template, logs, plain(Material.OAK_PLANKS)));
    }

    @Test
    void criteriaAllowAnyOtherData() {
        var matcher = matcher(false);
        var logs = ItemCriteria.of("#minecraft:logs");
        var named = plain(Material.OAK_LOG);
        named.setData(DataComponentTypes.ITEM_NAME, Component.text("Old log"));

        Assertions.assertTrue(matcher.matches(logs.template(), logs, named));
    }

    @Test
    void criteriaCheckTheEnchantmentCountAndTheRequiredLevels() {
        var matcher = matcher(false);
        var swords = new ItemCriteria(List.of("DIAMOND_SWORD"), 2, List.of()).withEnchantment("sharpness", 3);
        var template = swords.template();

        Assertions.assertFalse(matcher.matches(template, swords, enchanted(Material.DIAMOND_SWORD, 3)));
        Assertions.assertFalse(matcher.matches(
                template,
                swords,
                enchanted(Material.DIAMOND_SWORD, Map.of(Enchantment.SHARPNESS, 2, Enchantment.LOOTING, 1))));
        Assertions.assertTrue(matcher.matches(
                template,
                swords,
                enchanted(Material.DIAMOND_SWORD, Map.of(Enchantment.SHARPNESS, 4, Enchantment.LOOTING, 1))));
        Assertions.assertFalse(matcher.matches(template, swords, plain(Material.DIAMOND_SWORD)));
    }

    @Test
    void booksCountTheirStoredEnchantments() {
        var matcher = matcher(false);
        var books = ItemCriteria.of("ENCHANTED_BOOK").withEnchantment("sharpness", 5);

        Assertions.assertTrue(matcher.matches(books.template(), books, book(5)));
        Assertions.assertFalse(matcher.matches(books.template(), books, book(4)));
    }

    @Test
    void criteriaRefuseDamagedItemsUnlessAllowed() {
        var swords = ItemCriteria.of("DIAMOND_SWORD");

        Assertions.assertFalse(matcher(false).matches(swords.template(), swords, damaged(10)));
        Assertions.assertTrue(matcher(true).matches(swords.template(), swords, damaged(10)));
    }

    @Test
    void criteriaPreferPlainItems() {
        var matcher = matcher(false);
        var logs = ItemCriteria.of("#minecraft:logs");
        var modelled = plain(Material.OAK_LOG);
        modelled.setData(DataComponentTypes.ITEM_NAME, Component.text("Special log"));

        Assertions.assertEquals(0, matcher.extras(logs.template(), logs, plain(Material.OAK_LOG)));
        Assertions.assertEquals(1, matcher.extras(logs.template(), logs, modelled));
    }

    @Test
    void criteriaExtrasAreTheEnchantmentsBeyondTheCount() {
        var matcher = matcher(false);
        var swords = new ItemCriteria(List.of("DIAMOND_SWORD"), 1, List.of());
        var two = enchanted(Material.DIAMOND_SWORD, Map.of(Enchantment.SHARPNESS, 2, Enchantment.LOOTING, 1));

        Assertions.assertEquals(1, matcher.extras(swords.template(), swords, two));
        Assertions.assertEquals(0, matcher.extras(swords.template(), swords, enchanted(Material.DIAMOND_SWORD, 1)));
    }

    @Test
    void aDurabilityConditionTakesDamagedItemsWithEnoughLeft() {
        var kept = ItemCriteria.of("DIAMOND_SWORD").withMinDurability(80);

        Assertions.assertTrue(matcher(false).matches(kept.template(), kept, damaged(20)));
        Assertions.assertFalse(matcher(false).matches(kept.template(), kept, damaged(21)));
        Assertions.assertFalse(matcher(true).matches(kept.template(), kept, damaged(21)));
        Assertions.assertTrue(matcher(false).matches(kept.template(), kept, plain(Material.DIAMOND_SWORD)));
        Assertions.assertFalse(matcher(false).matches(kept.template(), kept, damaged(150)));
        var mixed = ItemCriteria.of("DIAMOND_SWORD", "STICK").withMinDurability(80);
        Assertions.assertTrue(matcher(false).matches(mixed.template(), mixed, plain(Material.STICK)));
    }

    @Test
    void aPotionConditionTakesOnlyThatPotionType() {
        var matcher = matcher(false);
        var swiftness = ItemCriteria.of("POTION", "TIPPED_ARROW").withPotion(PotionType.SWIFTNESS);

        Assertions.assertTrue(
                matcher.matches(swiftness.template(), swiftness, potion(Material.POTION, PotionType.SWIFTNESS)));
        Assertions.assertTrue(
                matcher.matches(swiftness.template(), swiftness, potion(Material.TIPPED_ARROW, PotionType.SWIFTNESS)));
        Assertions.assertFalse(
                matcher.matches(swiftness.template(), swiftness, potion(Material.POTION, PotionType.LONG_SWIFTNESS)));
        Assertions.assertFalse(matcher.matches(swiftness.template(), swiftness, plain(Material.POTION)));
        Assertions.assertEquals(
                0, matcher.extras(swiftness.template(), swiftness, potion(Material.POTION, PotionType.SWIFTNESS)));
        var coloured = plain(Material.POTION);
        coloured.setData(
                DataComponentTypes.POTION_CONTENTS,
                PotionContents.potionContents()
                        .potion(PotionType.SWIFTNESS)
                        .customColor(Color.RED)
                        .build());
        Assertions.assertTrue(matcher.matches(swiftness.template(), swiftness, coloured));
        Assertions.assertEquals(1, matcher.extras(swiftness.template(), swiftness, coloured));
    }

    private static ComponentStack potion(Material material, PotionType type) {
        var stack = plain(material);
        stack.setData(
                DataComponentTypes.POTION_CONTENTS,
                PotionContents.potionContents().potion(type).build());
        return stack;
    }
}
