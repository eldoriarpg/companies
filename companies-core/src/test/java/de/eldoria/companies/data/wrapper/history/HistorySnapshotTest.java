/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.history;

import de.eldoria.companies.components.order.HistoryOutcome;
import de.eldoria.companies.data.TestMappers;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

class HistorySnapshotTest {
    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    @Test
    void aSnapshotSurvivesTheJsonRoundTrip() {
        var sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        var worker = UUID.randomUUID();
        var snapshot = new HistorySnapshot(
                List.of(
                        new HistorySnapshot.Line(sword, true, 2, 1, 300.0),
                        new HistorySnapshot.Line(new ItemStack(Material.STONE), false, 64, 64, 32.5)),
                332.5,
                0,
                332.5,
                33.25,
                299.25,
                List.of(new HistorySnapshot.Worker(worker, 65, 332.5, 299.25)));
        var mapper = TestMappers.items();

        var read = HistorySnapshot.fromJson(mapper, snapshot.toJson(mapper));

        Assertions.assertEquals(snapshot.price(), read.price());
        Assertions.assertEquals(snapshot.net(), read.net());
        Assertions.assertEquals(snapshot.workers(), read.workers());
        var line = read.lines().getFirst();
        Assertions.assertEquals(Material.DIAMOND_SWORD, line.template().getType());
        Assertions.assertEquals(5, line.template().getEnchantmentLevel(Enchantment.SHARPNESS));
        Assertions.assertTrue(line.exact());
        Assertions.assertEquals(2, line.amount());
        Assertions.assertEquals(1, line.delivered());
        Assertions.assertEquals(32.5, read.lines().get(1).price());
    }

    @Test
    void entriesBelongToExactlyOneSide() {
        var snapshot = new HistorySnapshot(List.of(), 0, 0, 0, 0, 0, List.of());
        var now = LocalDateTime.now();
        var owner = UUID.randomUUID();

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new HistoryEntry(0, 1, owner, 2, HistoryOutcome.RECEIVED, "x", now, now, snapshot));
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new HistoryEntry(0, 1, null, null, HistoryOutcome.RECEIVED, "x", now, now, snapshot));
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> HistoryEntry.ofOwner(1, owner, HistoryOutcome.DELIVERED, "x", now, now, snapshot));
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> HistoryEntry.ofCompany(1, 2, HistoryOutcome.RECEIVED, "x", now, now, snapshot));
        Assertions.assertDoesNotThrow(
                () -> HistoryEntry.ofCompany(1, 2, HistoryOutcome.CANCELLED_BY_ADMIN, "x", now, now, snapshot));
    }
}
