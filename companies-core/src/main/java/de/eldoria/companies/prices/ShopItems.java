/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.prices;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * Turns the items and prices of shops into materials and prices of one item. Only plain items count: an item whose
 * data carries nothing beyond the defaults of its material, so a named, enchanted or otherwise changed item says
 * nothing about what its material costs. A price at or below zero and an amount below one count neither.
 */
public final class ShopItems {
    private ShopItems() {}

    /**
     * @param item an item of a shop
     * @return the name of its material, upper case, empty when the item is not plain
     */
    public static Optional<String> material(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir() || !item.getType().isItem()) return Optional.empty();
        if (!item.isSimilar(new ItemStack(item.getType()))) return Optional.empty();
        return Optional.of(item.getType().name().toUpperCase(Locale.ROOT));
    }

    /**
     * @param amount how many items the price is for
     * @param price  the price of all of them
     * @return whether the price says what an item costs: a finite price above zero for at least one item
     */
    public static boolean valid(int amount, double price) {
        return amount > 0 && price > 0 && Double.isFinite(price);
    }
}
