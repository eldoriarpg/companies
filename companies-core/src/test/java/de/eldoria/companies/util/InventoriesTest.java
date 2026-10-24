/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import de.eldoria.companies.data.wrapper.order.ItemAmount;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

class InventoriesTest {
    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    @Test
    void fitIsLimitedByEarlierFits() {
        var storage = new ItemStack[] {null, null};

        Assertions.assertEquals(64, Inventories.fit(storage, new ItemStack(Material.STONE), 64));
        Assertions.assertEquals(16, Inventories.fit(storage, new ItemStack(Material.ENDER_PEARL), 20));
        Assertions.assertEquals(0, Inventories.fit(storage, new ItemStack(Material.DIRT), 1));
    }

    @Test
    void splitRespectsMaxStackSize() {
        var stacks = Inventories.split(new ItemStack(Material.ENDER_PEARL), 40);

        Assertions.assertEquals(3, stacks.size());
        Assertions.assertEquals(
                40, stacks.stream().mapToInt(ItemStack::getAmount).sum());
        Assertions.assertTrue(stacks.stream().allMatch(stack -> stack.getAmount() <= 16));
    }

    @Test
    void placeSplitsWhatFitsFromWhatWaits() {
        var storage = new ItemStack[] {null, new ItemStack(Material.STONE, 60)};

        var placement = Inventories.place(
                storage,
                List.of(
                        new ItemAmount(new ItemStack(Material.STONE), 70),
                        new ItemAmount(new ItemStack(Material.DIRT), 5)));

        Assertions.assertEquals(
                List.of(Material.STONE),
                placement.fitting().stream().map(item -> item.stack().getType()).toList());
        Assertions.assertEquals(68, placement.fitting().get(0).amount());
        Assertions.assertEquals(2, placement.remaining().get(0).amount());
        Assertions.assertEquals(
                Material.DIRT, placement.remaining().get(1).stack().getType());
        Assertions.assertEquals(7, placement.remainingAmount());
    }

    @Test
    void placeKeepsEverythingWhenTheStorageIsEmptyEnough() {
        var placement = Inventories.place(
                new ItemStack[] {null, null}, List.of(new ItemAmount(new ItemStack(Material.ENDER_PEARL), 32)));

        Assertions.assertEquals(32, placement.fitting().get(0).amount());
        Assertions.assertTrue(placement.remaining().isEmpty());
        Assertions.assertEquals(0, placement.remainingAmount());
    }
}
