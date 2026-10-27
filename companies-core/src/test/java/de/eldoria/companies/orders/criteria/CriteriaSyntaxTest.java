/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.criteria;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.order.ItemCriteria;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.companies.orders.OrderDraftService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CriteriaSyntaxTest {
    private final OrderSettings settings = new OrderSettings();

    @TempDir
    Path directory;

    private OrderDraftService drafts;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        SqLiteTestDatabase.create(directory);
        settings.maxMaterials(2);
        settings.maxItems(100);
        var configuration = new Configuration(plugin) {
            @Override
            public OrderSettings orderSetting() {
                return settings;
            }
        };
        drafts = new OrderDraftService(plugin, new SqLiteOrderData(null, SqLiteTestDatabase.mapper()), configuration);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void itemsTagsCountAndEnchantmentsAreRead() {
        var criteria = CriteriaSyntax.parse(" #logs, bamboo_block  enchants:3 minecraft:sharpness:3 looting:1");

        assertEquals(List.of("#minecraft:logs", "BAMBOO_BLOCK"), criteria.items());
        assertEquals(3, criteria.minEnchantments());
        assertEquals(
                List.of(new ItemCriteria.Required("sharpness", 3), new ItemCriteria.Required("looting", 1)),
                criteria.enchantments());
        assertEquals(
                List.of("OAK_LOG"), CriteriaSyntax.parse("minecraft:oak_log").items());
    }

    @Test
    void theTextFormIsReadBackUnchanged() {
        var criteria = new ItemCriteria(List.of("#minecraft:logs", "BAMBOO_BLOCK"), 3, List.of())
                .withEnchantment("sharpness", 3);

        var text = CriteriaSyntax.format(criteria);

        assertEquals("#logs bamboo_block enchants:3 sharpness:3", text);
        assertEquals(criteria, CriteriaSyntax.parse(text));
    }

    @Test
    void onlyTagsSeveralEntriesOrConditionsAreCriteria() {
        assertTrue(CriteriaSyntax.isCriteria("#logs"));
        assertTrue(CriteriaSyntax.isCriteria("oak_log birch_log"));
        assertTrue(CriteriaSyntax.isCriteria("oak_log,birch_log"));
        assertTrue(CriteriaSyntax.isCriteria("sharpness:3"));
        assertFalse(CriteriaSyntax.isCriteria("oak_log"));
        assertFalse(CriteriaSyntax.isCriteria("minecraft:oak_log"));
        assertFalse(CriteriaSyntax.isCriteria("  "));
    }

    @Test
    void unreadableTextIsRefused() {
        assertEquals("order.criteria.error.syntax", refusal("enchants:2"));
        assertEquals("order.criteria.error.syntax", refusal("stone sharpness:0"));
        assertEquals("order.criteria.error.syntax", refusal("stone sharpness:1 sharpness:2"));
        assertEquals("order.criteria.error.syntax", refusal("stone enchants:99999999999"));
        assertEquals("order.criteria.error.syntax", refusal("   "));
        assertEquals("order.criteria.error.syntax", refusal("stone enchants:-1"));
        assertEquals("order.criteria.error.syntax", refusal("stone enchants:two"));
        assertEquals("order.criteria.error.syntax", refusal("stone enchants:1 enchants:2"));
        assertEquals("order.criteria.error.syntax", refusal("diamond_sword sharpness:-2"));
    }

    @Test
    void draftsTakeCheckedCriteriaAndMergeEqualOnes() {
        var draft = new OrderBuilder(UUID.randomUUID(), "logs");
        var logs = CriteriaSyntax.parse("#logs");

        assertEquals(1, drafts.add(draft, logs, 40, 1.0));
        assertEquals(1, drafts.add(draft, CriteriaSyntax.parse("#minecraft:logs"), 30, 2.0));
        assertEquals(2, drafts.add(draft, CriteriaSyntax.parse("oak_planks birch_planks"), 10, 1.0));
        assertEquals(2, drafts.add(draft, CriteriaSyntax.parse("birch_planks, oak_planks"), 40, 1.0));
        assertEquals(100, draft.amount());
        assertEquals(logs, draft.line(1).orElseThrow().criteria());

        var refused = assertThrows(
                CommandFailure.class, () -> drafts.add(draft, CriteriaSyntax.parse("#logs sharpness:1"), 1, 1.0));
        assertEquals("order.criteria.error.enchantmentNotApplicable", refused.localeKey());
        assertEquals(2, draft.lineCount());
    }

    private static String refusal(String text) {
        return assertThrows(CommandFailure.class, () -> CriteriaSyntax.parse(text))
                .localeKey();
    }
}
