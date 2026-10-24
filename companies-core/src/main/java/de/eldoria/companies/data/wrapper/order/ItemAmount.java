/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

import org.bukkit.inventory.ItemStack;

/**
 * An amount of one item, which may exceed the max stack size.
 *
 * @param stack  the item
 * @param amount amount of items
 */
public record ItemAmount(ItemStack stack, int amount) {}
