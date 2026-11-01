/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.price;

import org.bukkit.inventory.ItemStack;

/**
 * Money that changed hands for items in a shop.
 *
 * @param item   the item traded; only plain items, without a name, enchantments or other changes, count
 * @param amount how many items were traded
 * @param price  the money paid for all {@code amount} items, in the server's main currency
 * @param side   whether the player bought or sold
 * @param origin who set the price
 */
public record ShopTrade(ItemStack item, int amount, double price, ShopSide side, ShopOrigin origin) {}
