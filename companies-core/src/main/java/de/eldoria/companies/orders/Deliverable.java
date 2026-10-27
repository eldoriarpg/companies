/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.data.wrapper.order.FullOrder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Works out how much of an order a player could deliver with the items they carry.
 * <p>
 * The items are those a delivery would take: the storage slots and the content of a shulker box held in the main
 * hand. Whether an item counts for a line is decided by the {@link ItemMatcher}, like on delivery.
 */
public final class Deliverable {
    private Deliverable() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Copies the items a player could deliver. Has to be called on the main thread.
     *
     * @param player player to copy the items of
     * @return copies of the items
     */
    public static List<ItemStack> available(Player player) {
        return ItemSources.snapshot(ItemSources.collect(player));
    }

    /**
     * The amount of items of an order the player could deliver. Every item counts for one line only.
     *
     * @param matcher   decides which items match a line
     * @param available items the player carries
     * @param order     order to fill
     * @return sum over all lines of what is missing and available
     */
    public static int amount(ItemMatcher matcher, List<ItemStack> available, FullOrder order) {
        var left = available.stream().mapToInt(ItemStack::getAmount).toArray();
        var total = 0;
        for (var content : order.contents()) {
            var missing = Math.max(0, content.missing());
            for (var index = 0; index < left.length && missing > 0; index++) {
                if (left[index] <= 0 || !matcher.matches(content, available.get(index))) continue;
                var counted = Math.min(missing, left[index]);
                left[index] -= counted;
                missing -= counted;
                total += counted;
            }
        }
        return total;
    }
}
