/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import org.bukkit.inventory.ItemStack;

/**
 * A place a delivery takes items from: a storage slot of a player or a slot of the shulker box in the main hand.
 * <p>
 * Slots are used on the main thread only.
 */
public interface ItemSlot {
    /**
     * The item currently in the slot. It must not be changed; use {@link #take(int)}.
     *
     * @return the item, empty when the slot holds nothing
     */
    ItemStack stack();

    /**
     * Removes items from the slot.
     *
     * @param amount number of items to remove, at most the amount in the slot
     */
    void take(int amount);
}
