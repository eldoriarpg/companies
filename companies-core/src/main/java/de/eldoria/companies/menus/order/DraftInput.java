/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.order;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

/**
 * Reads what a player entered into the inputs of the order builder dialogs.
 */
public final class DraftInput {
    private DraftInput() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Reads a unit price. A comma is accepted as decimal separator.
     *
     * @param text entered text
     * @return the price, empty unless it is a finite number of zero or more
     */
    public static Optional<Double> unitPrice(@Nullable String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        try {
            var price = Double.parseDouble(text.strip().replace(',', '.'));
            if (!Double.isFinite(price) || price < 0) return Optional.empty();
            return Optional.of(price);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Reads the value of an amount slider.
     *
     * @param value value of the slider, null when the client sent none
     * @param max   largest allowed amount
     * @return the amount, rounded and kept between 1 and {@code max}
     */
    public static int amount(@Nullable Float value, int max) {
        if (value == null || value.isNaN()) return 1;
        return (int) Math.max(1, Math.min(Math.max(1, max), Math.round(value)));
    }

    /**
     * Reads a material that can be held as an item, such as {@code oak_log} or {@code minecraft:oak_log}.
     *
     * @param text entered text
     * @return the material, empty when there is no such item
     */
    public static Optional<Material> material(@Nullable String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        var key = NamespacedKey.fromString(text.strip().toLowerCase(Locale.ROOT).replace(' ', '_'));
        if (key == null) return Optional.empty();
        return Optional.ofNullable(Registry.MATERIAL.get(key))
                .filter(material -> material.isItem() && !material.isAir());
    }

    /**
     * Writes a unit price as the initial text of a price input.
     *
     * @param price price of one item
     * @return the price without trailing zeros, such as {@code 1.5} or {@code 2}
     */
    public static String unitPrice(double price) {
        return BigDecimal.valueOf(price).stripTrailingZeros().toPlainString();
    }
}
