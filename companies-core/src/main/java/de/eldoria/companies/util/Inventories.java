/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import de.eldoria.companies.data.wrapper.order.ItemAmount;
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
     * Splits items into the part that fits into the storage and the part that does not.
     * <p>
     * The storage array is changed in place like in {@link #fit(ItemStack[], ItemStack, int)}.
     *
     * @param storage storage slots to fill
     * @param items   items to place
     * @return the fitting and the remaining amounts
     */
    public static Placement place(ItemStack[] storage, List<ItemAmount> items) {
        List<ItemAmount> fitting = new ArrayList<>();
        List<ItemAmount> remaining = new ArrayList<>();
        for (var item : items) {
            var fit = fit(storage, item.stack(), item.amount());
            if (fit > 0) fitting.add(new ItemAmount(item.stack(), fit));
            if (fit < item.amount()) remaining.add(new ItemAmount(item.stack(), item.amount() - fit));
        }
        return new Placement(fitting, remaining);
    }

    /**
     * Adds the stacks to the storage of the player and drops everything that does not fit at the location of the player.
     *
     * @param player player receiving the items
     * @param stacks items to give
     */
    public static void give(Player player, List<ItemStack> stacks) {
        for (var leftover : add(player, stacks)) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    /**
     * Adds the stacks to the storage of the player.
     *
     * @param player player receiving the items
     * @param stacks items to give
     * @return the stacks that did not fit
     */
    public static List<ItemStack> add(Player player, List<ItemStack> stacks) {
        if (stacks.isEmpty()) return List.of();
        return List.copyOf(
                player.getInventory().addItem(stacks.toArray(ItemStack[]::new)).values());
    }

    /**
     * The result of placing items into a storage.
     *
     * @param fitting   amounts that fit
     * @param remaining amounts that did not fit
     */
    public record Placement(List<ItemAmount> fitting, List<ItemAmount> remaining) {
        /**
         * The number of items that did not fit.
         *
         * @return remaining items
         */
        public int remainingAmount() {
            return remaining.stream().mapToInt(ItemAmount::amount).sum();
        }
    }
}
