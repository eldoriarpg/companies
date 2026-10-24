/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.definition;

import de.eldoria.companies.commands.support.CommandFailure;
import org.bukkit.DyeColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Items defined on the dashboard: what survival can make is built, the rest refused.
 */
class ItemDefinitionsTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ItemDefinition item(String material, Map<String, Integer> enchantments) {
        return new ItemDefinition(material, enchantments, null, null, null);
    }

    private static String refusal(ItemDefinition definition) {
        return assertThrows(CommandFailure.class, () -> ItemDefinitions.build(definition))
                .localeKey();
    }

    @Test
    void enchantmentsUpToTheirHighestLevelAreApplied() {
        var sword = ItemDefinitions.build(item("diamond_sword", Map.of("minecraft:sharpness", 5, "looting", 3)));

        assertEquals(5, sword.getEnchantmentLevel(Enchantment.SHARPNESS));
        assertEquals(3, sword.getEnchantmentLevel(Enchantment.LOOTING));
        assertEquals(1, sword.getAmount());
    }

    @Test
    void anEnchantedBookStoresItsEnchantments() {
        var book = ItemDefinitions.build(item("enchanted_book", Map.of("mending", 1)));

        assertTrue(((EnchantmentStorageMeta) book.getItemMeta()).hasStoredEnchant(Enchantment.MENDING));
    }

    @Test
    void whatSurvivalCannotMakeIsRefused() {
        assertEquals("dashboard.item.unknownMaterial", refusal(item("nothing", Map.of())));
        assertEquals("dashboard.item.unknownMaterial", refusal(item("water", Map.of())));
        assertEquals("dashboard.item.enchantmentLevel", refusal(item("diamond_sword", Map.of("sharpness", 6))));
        assertEquals("dashboard.item.enchantmentNotApplicable", refusal(item("stick", Map.of("sharpness", 1))));
        assertEquals(
                "dashboard.item.enchantmentConflict",
                refusal(item("diamond_sword", Map.of("sharpness", 1, "smite", 1))));
        assertEquals("dashboard.item.unknownEnchantment", refusal(item("diamond_sword", Map.of("speed", 1))));
    }

    @Test
    void potionsDyesAndTrimsGoOnlyWhereTheyBelong() {
        var potion = ItemDefinitions.build(new ItemDefinition("splash_potion", Map.of(), "strong_healing", null, null));
        assertEquals(PotionType.STRONG_HEALING, ((PotionMeta) potion.getItemMeta()).getBasePotionType());

        var tunic = ItemDefinitions.build(new ItemDefinition("leather_chestplate", Map.of(), null, "red", null));
        assertEquals(DyeColor.RED.getColor(), ((LeatherArmorMeta) tunic.getItemMeta()).getColor());

        var helmet = ItemDefinitions.build(
                new ItemDefinition("iron_helmet", Map.of(), null, null, new ItemDefinition.Trim("coast", "gold")));
        assertTrue(((ArmorMeta) helmet.getItemMeta()).hasTrim());

        assertEquals(
                "dashboard.item.potionNotApplicable",
                refusal(new ItemDefinition("stick", Map.of(), "healing", null, null)));
        assertEquals(
                "dashboard.item.dyeNotApplicable",
                refusal(new ItemDefinition("iron_chestplate", Map.of(), null, "red", null)));
        assertEquals(
                "dashboard.item.trimNotApplicable",
                refusal(new ItemDefinition("stick", Map.of(), null, null, new ItemDefinition.Trim("coast", "gold"))));
    }

    @Test
    void aPotionOnlyCommandsGiveIsUnknown() {
        assertEquals(
                "dashboard.item.unknownPotion", refusal(new ItemDefinition("potion", Map.of(), "luck", null, null)));
        assertTrue(
                ItemDefinitions.build(new ItemDefinition("potion", Map.of(), "awkward", null, null))
                                .getItemMeta()
                        instanceof PotionMeta);
    }
}
