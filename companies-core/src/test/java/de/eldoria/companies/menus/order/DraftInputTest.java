/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.order;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DraftInputTest {
    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    @Test
    void unitPriceAcceptsNonNegativeFiniteNumbers() {
        assertEquals(Optional.of(1.5), DraftInput.unitPrice("1.5"));
        assertEquals(Optional.of(1.5), DraftInput.unitPrice(" 1,5 "));
        assertEquals(Optional.of(0.0), DraftInput.unitPrice("0"));
        assertEquals(Optional.of(20.0), DraftInput.unitPrice("2e1"));
    }

    @Test
    void unitPriceRejectsEverythingElse() {
        assertEquals(Optional.empty(), DraftInput.unitPrice(null));
        assertEquals(Optional.empty(), DraftInput.unitPrice(""));
        assertEquals(Optional.empty(), DraftInput.unitPrice("  "));
        assertEquals(Optional.empty(), DraftInput.unitPrice("-1"));
        assertEquals(Optional.empty(), DraftInput.unitPrice("NaN"));
        assertEquals(Optional.empty(), DraftInput.unitPrice("Infinity"));
        assertEquals(Optional.empty(), DraftInput.unitPrice("1e400"));
        assertEquals(Optional.empty(), DraftInput.unitPrice("ten"));
    }

    @Test
    void unitPriceIsWrittenWithoutTrailingZeros() {
        assertEquals("1.5", DraftInput.unitPrice(1.50));
        assertEquals("2", DraftInput.unitPrice(2.0));
        assertEquals("0", DraftInput.unitPrice(0.0));
        assertEquals("0.29", DraftInput.unitPrice(0.29));
    }

    @Test
    void amountIsRoundedAndKeptWithinTheSlider() {
        assertEquals(3, DraftInput.amount(3f, 10));
        assertEquals(4, DraftInput.amount(3.6f, 10));
        assertEquals(10, DraftInput.amount(64f, 10));
        assertEquals(1, DraftInput.amount(0f, 10));
        assertEquals(1, DraftInput.amount(-5f, 10));
        assertEquals(1, DraftInput.amount(null, 10));
        assertEquals(1, DraftInput.amount(Float.NaN, 10));
        assertEquals(1, DraftInput.amount(5f, 0));
    }

    @Test
    void materialIsReadFromItsKey() {
        assertEquals(Optional.of(Material.OAK_LOG), DraftInput.material("oak_log"));
        assertEquals(Optional.of(Material.OAK_LOG), DraftInput.material(" minecraft:OAK_LOG "));
        assertEquals(Optional.of(Material.OAK_LOG), DraftInput.material("Oak Log"));
    }

    @Test
    void materialRejectsUnknownNamesAndNonItems() {
        assertEquals(Optional.empty(), DraftInput.material(null));
        assertEquals(Optional.empty(), DraftInput.material(""));
        assertEquals(Optional.empty(), DraftInput.material("gold_coin"));
        assertEquals(Optional.empty(), DraftInput.material("air"));
        assertEquals(Optional.empty(), DraftInput.material("no:such:key"));
    }
}
