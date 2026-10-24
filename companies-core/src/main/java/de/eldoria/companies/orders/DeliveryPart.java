/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import org.bukkit.inventory.ItemStack;

/**
 * Items a worker offers to one line of an order. Every distinct item is offered as its own part.
 *
 * @param line   line of the order the items are delivered to
 * @param stack  the offered item; its own amount is ignored
 * @param amount amount of items offered
 */
public record DeliveryPart(int line, ItemStack stack, int amount) {}
