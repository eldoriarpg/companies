/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class InventoriesTest {

    @Test
    void fitIsLimitedByEarlierFits() {
        var storage = new ItemStack[]{null, null};

        Assertions.assertEquals(64, Inventories.fit(storage, new ItemStack(Material.STONE), 64));
        Assertions.assertEquals(16, Inventories.fit(storage, new ItemStack(Material.ENDER_PEARL), 20));
        Assertions.assertEquals(0, Inventories.fit(storage, new ItemStack(Material.DIRT), 1));
    }

    @Test
    void splitRespectsMaxStackSize() {
        var stacks = Inventories.split(new ItemStack(Material.ENDER_PEARL), 40);

        Assertions.assertEquals(3, stacks.size());
        Assertions.assertEquals(40, stacks.stream().mapToInt(ItemStack::getAmount).sum());
        Assertions.assertTrue(stacks.stream().allMatch(stack -> stack.getAmount() <= 16));
    }
}
