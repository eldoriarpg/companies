/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

import de.eldoria.companies.components.order.IDeliveredStack;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Items a worker delivered to one line of an order, stored exactly as they were delivered.
 *
 * @param id     id of the stored delivery
 * @param worker worker who delivered the items
 * @param stack  delivered item with an amount of one
 * @param amount amount of items delivered
 */
public record DeliveredStack(int id, UUID worker, ItemStack stack, int amount) implements IDeliveredStack {}
