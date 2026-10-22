/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Helpers to hand items to players. Every method touching a {@link Player} has to be called on the main thread.
 */
public final class Inventories {
    private Inventories() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Creates a detached copy of the storage slots of a player, which can be used to simulate adding items.
     *
     * @param player player to copy the storage from
     * @return copy of the storage contents
     */
    public static ItemStack[] storageCopy(Player player) {
        return Arrays.stream(player.getInventory().getStorageContents())
                .map(stack -> stack == null ? null : stack.clone())
                .toArray(ItemStack[]::new);
    }

    /**
     * Adds as many items as possible to the given storage and reports how many were added.
     * <p>
     * The storage array is changed in place, which allows to check several stacks one after another against the same storage.
     *
     * @param storage storage slots to fill
     * @param stack   item to add
     * @param amount  amount of the item to add
     * @return amount of items which fit into the storage
     */
    public static int fit(ItemStack[] storage, ItemStack stack, int amount) {
        var remaining = amount;
        var maxStackSize = Math.max(1, stack.getMaxStackSize());
        for (var i = 0; i < storage.length && remaining > 0; i++) {
            var slot = storage[i];
            if (slot == null || slot.getType().isAir()) {
                var added = Math.min(maxStackSize, remaining);
                var newStack = stack.clone();
                newStack.setAmount(added);
                storage[i] = newStack;
                remaining -= added;
                continue;
            }
            if (!slot.isSimilar(stack)) continue;
            var added = Math.min(Math.max(0, maxStackSize - slot.getAmount()), remaining);
            slot.setAmount(slot.getAmount() + added);
            remaining -= added;
        }
        return amount - remaining;
    }

    /**
     * Splits an amount of items into stacks which respect the max stack size.
     *
     * @param stack  item to split
     * @param amount total amount of items
     * @return stacks containing the amount of items in total
     */
    public static List<ItemStack> split(ItemStack stack, int amount) {
        List<ItemStack> stacks = new ArrayList<>();
        var remaining = amount;
        while (remaining > 0) {
            var size = Math.min(Math.max(1, stack.getMaxStackSize()), remaining);
            var newStack = stack.clone();
            newStack.setAmount(size);
            stacks.add(newStack);
            remaining -= size;
        }
        return stacks;
    }

    /**
     * Adds the stacks to the storage of the player and drops everything that does not fit at the location of the player.
     *
     * @param player player receiving the items
     * @param stacks items to give
     */
    public static void give(Player player, List<ItemStack> stacks) {
        if (stacks.isEmpty()) return;
        var leftovers = player.getInventory().addItem(stacks.toArray(ItemStack[]::new));
        for (var leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }
}
