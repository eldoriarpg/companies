/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.price;

import org.bukkit.inventory.ItemStack;

/**
 * A price a shop offers right now, such as an open player shop, an open auction or an item of a GUI shop.
 *
 * @param item   the item offered; only plain items, without a name, enchantments or other changes, count
 * @param amount how many items the price is for, such as the quantity of a sign or the stack of an auction
 * @param price  the price of all {@code amount} items, in the server's main currency
 * @param side   whether the player buys or sells at this price
 * @param origin who set the price
 */
public record ShopQuote(ItemStack item, int amount, double price, ShopSide side, ShopOrigin origin) {}
