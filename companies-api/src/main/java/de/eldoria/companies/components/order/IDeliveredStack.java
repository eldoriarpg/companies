/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.order;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Items a worker delivered to one line of an order, stored exactly as they were delivered.
 * <p>
 * The owner receives these items, and they go back to the worker when the order is released.
 */
public interface IDeliveredStack {
    /**
     * The worker who delivered the items.
     *
     * @return id of the worker
     */
    UUID worker();

    /**
     * The delivered item with an amount of one.
     *
     * @return delivered item
     */
    ItemStack stack();

    /**
     * How many of the item were delivered.
     *
     * @return amount of items
     */
    int amount();
}
