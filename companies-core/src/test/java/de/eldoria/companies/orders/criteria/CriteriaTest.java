/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.criteria;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.order.ItemCriteria;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.history.HistorySnapshot;
import de.eldoria.companies.data.wrapper.series.SeriesLine;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CriteriaTest {
    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    @Test
    void entriesAreNormalized() {
        var criteria = new ItemCriteria(
                List.of(" oak_log", "minecraft:birch_log", "#logs", "#Minecraft:Planks", "OAK_LOG"),
                0,
                List.of(new ItemCriteria.Required("Sharpness", 2)));

        assertEquals(List.of("OAK_LOG", "BIRCH_LOG", "#minecraft:logs", "#minecraft:planks"), criteria.items());
        assertEquals("minecraft:sharpness", criteria.enchantments().getFirst().enchantment());
        assertEquals(1, criteria.minEnchantments());
    }

    @Test
    void brokenCriteriaAreRefusedWhenBuilt() {
        assertThrows(IllegalArgumentException.class, () -> new ItemCriteria(List.of(), 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> ItemCriteria.of(" "));
        assertThrows(IllegalArgumentException.class, () -> new ItemCriteria(List.of("STONE"), -1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ItemCriteria.Required("sharpness", 0));
        assertThrows(IllegalArgumentException.class, () -> ItemCriteria.of("DIAMOND_SWORD")
                .withEnchantment("sharpness", 1)
                .withEnchantment("minecraft:sharpness", 2));
    }

    @Test
    void theSetResolvesTagsAndKeepsTheOrder() {
        var criteria = ItemCriteria.of("BAMBOO_BLOCK", "#minecraft:logs");

        var materials = List.copyOf(criteria.materials());

        assertEquals(Material.BAMBOO_BLOCK, materials.getFirst());
        assertTrue(materials.contains(Material.OAK_LOG));
        assertTrue(criteria.accepts(Material.SPRUCE_LOG));
        assertFalse(criteria.accepts(Material.OAK_PLANKS));
        assertEquals(Material.BAMBOO_BLOCK, criteria.template().getType());
        assertEquals(
                Material.BARRIER,
                ItemCriteria.of("#minecraft:no_such_tag").template().getType());
    }

    @Test
    void fineCriteriaPass() {
        assertTrue(CriteriaRules.problem(ItemCriteria.of("#minecraft:logs")).isEmpty());
        assertTrue(CriteriaRules.problem(new ItemCriteria(List.of("DIAMOND_SWORD", "IRON_SWORD"), 2, List.of())
                        .withEnchantment("sharpness", 3))
                .isEmpty());
        assertTrue(CriteriaRules.problem(ItemCriteria.of("ENCHANTED_BOOK").withEnchantment("mending", 1))
                .isEmpty());
    }

    @Test
    void unknownItemsTagsAndEnchantmentsAreRefused() {
        assertEquals("order.criteria.error.unknownItem", problem(ItemCriteria.of("NO_SUCH_ITEM")));
        assertEquals("order.criteria.error.unknownItem", problem(ItemCriteria.of("WATER")));
        assertEquals("order.criteria.error.unknownTag", problem(ItemCriteria.of("#minecraft:no_such_tag")));
        assertEquals(
                "order.criteria.error.unknownEnchantment",
                problem(ItemCriteria.of("DIAMOND_SWORD").withEnchantment("minecraft:no_such_enchantment", 1)));
    }

    @Test
    void enchantmentsNoItemCanCarryAreRefused() {
        assertEquals(
                "order.criteria.error.enchantmentLevel",
                problem(ItemCriteria.of("DIAMOND_SWORD").withEnchantment("sharpness", 6)));
        assertEquals(
                "order.criteria.error.enchantmentNotApplicable",
                problem(ItemCriteria.of("#minecraft:logs").withEnchantment("sharpness", 1)));
        assertEquals(
                "order.criteria.error.enchantmentConflict",
                problem(ItemCriteria.of("DIAMOND_SWORD")
                        .withEnchantment("sharpness", 1)
                        .withEnchantment("smite", 1)));
        assertEquals(
                "order.criteria.error.enchantmentCount", problem(new ItemCriteria(List.of("STICK"), 1, List.of())));
    }

    @Test
    void oneItemHasToMeetAllConditionsAtOnce() {
        assertEquals(
                "order.criteria.error.enchantmentsApart",
                problem(ItemCriteria.of("DIAMOND_SWORD", "BOW")
                        .withEnchantment("sharpness", 1)
                        .withEnchantment("power", 1)));
        assertEquals(
                "order.criteria.error.enchantmentCount",
                problem(new ItemCriteria(List.of("DIAMOND_SWORD"), 10, List.of())));
        assertEquals("none", problem(new ItemCriteria(List.of("DIAMOND_SWORD"), 3, List.of())));
        assertEquals(
                "none",
                problem(ItemCriteria.of("DIAMOND_SWORD", "BOW")
                        .withEnchantment("power", 1)
                        .withEnchantment("unbreaking", 1)));
    }

    @Test
    void thePlainTextNamesItemsAndConditions() {
        var criteria = new ItemCriteria(List.of("#minecraft:logs", "BAMBOO_BLOCK"), 3, List.of())
                .withEnchantment("sharpness", 3)
                .withEnchantment("looting", 1);

        assertEquals("#Logs / Bamboo Block (✦ ≥3, Sharpness III+, Looting)", CriteriaText.plain(criteria));
        assertEquals("#Logs", CriteriaText.plain(ItemCriteria.of("#minecraft:logs")));
        assertEquals(
                "Stone / Dirt / Sand / +1", CriteriaText.plain(ItemCriteria.of("STONE", "DIRT", "SAND", "GRAVEL")));
    }

    @Test
    void criteriaSurviveTheStorageMapper() throws Exception {
        var criteria = new ItemCriteria(List.of("#minecraft:logs"), 2, List.of()).withEnchantment("unbreaking", 2);
        var mapper = SqLiteTestDatabase.mapper();

        var json = mapper.writeValueAsString(criteria);

        assertEquals(criteria, mapper.readValue(json, ItemCriteria.class));
        assertFalse(json.contains("template"));
    }

    @Test
    void seriesAndHistoryLinesKeepTheirCriteria() throws Exception {
        var mapper = SqLiteTestDatabase.mapper();
        var logs = ItemCriteria.of("#minecraft:logs");
        var series = new SeriesLine(logs.template(), false, 64, 2.0, logs);
        var history = new HistorySnapshot.Line(logs.template(), false, 64, 10, 128.0, logs);

        var readSeries = mapper.readValue(mapper.writeValueAsString(series), SeriesLine.class);
        var readHistory = mapper.readValue(mapper.writeValueAsString(history), HistorySnapshot.Line.class);
        var plain = new SeriesLine(new ItemStack(Material.STONE), false, 1, 1.0);
        var readPlain = mapper.readValue(mapper.writeValueAsString(plain), SeriesLine.class);

        assertEquals(logs, readSeries.criteria());
        assertEquals(logs, readSeries.toDraftLine().criteria());
        assertEquals(logs, readHistory.criteria());
        assertNull(readPlain.criteria());
    }

    private static String problem(ItemCriteria criteria) {
        return CriteriaRules.problem(criteria).map(CommandFailure::localeKey).orElse("none");
    }
}
