/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.order;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderDraftTest {
    @BeforeAll
    static void setUp() {
        MockBukkit.mock();
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void addsMaterialAndExactLines() {
        var draft = IOrderDraft.of("order");
        var template = ItemStack.of(Material.DIAMOND_SWORD, 3);

        draft.addLine(Material.STONE, 64, 0.5);
        draft.addLine(template, 2, 100.0);

        var lines = draft.lines();
        assertEquals(2, lines.size());
        assertFalse(lines.get(0).exact());
        assertEquals(Material.STONE, lines.get(0).template().getType());
        assertTrue(lines.get(1).exact());
        assertEquals(1, lines.get(1).template().getAmount());
        assertEquals(3, template.getAmount());
        assertEquals(66, draft.amount());
        assertEquals(232.0, draft.price());
    }

    @Test
    void editsLines() {
        var draft = IOrderDraft.of("order");
        var line = draft.addLine(Material.STONE, 10, 1.0);

        line.amount(20);
        line.unitPrice(2.5);
        draft.name("renamed");

        assertEquals(20, line.amount());
        assertEquals(2.5, line.unitPrice());
        assertEquals(50.0, line.price());
        assertEquals(50.0, draft.price());
        assertEquals("renamed", draft.name());
    }

    @Test
    void removesLinesWhileIterating() {
        var draft = IOrderDraft.of("order");
        draft.addLine(Material.STONE, 10, 1.0);
        draft.addLine(Material.DIRT, 10, 1.0);
        draft.addLine(Material.STONE, 5, 1.0);

        for (var line : draft.lines()) {
            if (line.template().getType() == Material.STONE) line.remove();
        }

        assertEquals(
                List.of(Material.DIRT),
                draft.lines().stream().map(l -> l.template().getType()).toList());
        assertEquals(10, draft.amount());
    }

    @Test
    void removedLineRefusesEdits() {
        var draft = IOrderDraft.of("order");
        var line = draft.addLine(Material.STONE, 10, 1.0);

        line.remove();
        line.remove();

        assertTrue(line.removed());
        assertTrue(draft.lines().isEmpty());
        assertThrows(IllegalStateException.class, () -> line.amount(5));
        assertThrows(IllegalStateException.class, () -> line.unitPrice(5));
    }

    @Test
    void linesAreASnapshot() {
        var draft = IOrderDraft.of("order");
        var lines = draft.lines();

        draft.addLine(Material.STONE, 1, 1.0);

        assertTrue(lines.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> draft.lines().clear());
    }

    @Test
    void templateIsCopied() {
        var draft = IOrderDraft.of("order");
        var line = draft.addLine(Material.STONE, 1, 1.0);

        line.template().setAmount(10);

        assertEquals(1, line.template().getAmount());
    }

    @Test
    void rejectsInvalidValues() {
        var draft = IOrderDraft.of("order");
        var line = draft.addLine(Material.STONE, 1, 1.0);

        assertThrows(IllegalArgumentException.class, () -> IOrderDraft.of(" "));
        assertThrows(IllegalArgumentException.class, () -> draft.name(""));
        assertThrows(IllegalArgumentException.class, () -> draft.addLine(Material.AIR, 1, 1.0));
        assertThrows(IllegalArgumentException.class, () -> draft.addLine(Material.WATER, 1, 1.0));
        assertThrows(IllegalArgumentException.class, () -> draft.addLine(Material.STONE, 0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> draft.addLine(Material.STONE, 1, -1.0));
        assertThrows(IllegalArgumentException.class, () -> draft.addLine(Material.STONE, 1, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> line.amount(0));
        assertThrows(IllegalArgumentException.class, () -> line.unitPrice(Double.POSITIVE_INFINITY));
        assertEquals(1, draft.lines().size());
        assertEquals("order", draft.name());
    }
}
