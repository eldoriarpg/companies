/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.inventory.ItemStack;

import java.math.BigInteger;
import java.util.Locale;

/**
 * Placeholders for menu texts, used as {@code <key>} in the text.
 * <p>
 * Text values are inserted unparsed, so player input such as a name {@code <red>x} shows literally. Every key is
 * checked by {@link Keys#require(String)}.
 */
public final class Values {
    private Values() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * A plain text value, never parsed as MiniMessage.
     *
     * @param key   placeholder key
     * @param value text to insert
     * @return placeholder
     */
    public static TagResolver.Single text(String key, String value) {
        return Placeholder.unparsed(Keys.require(key), value);
    }

    /**
     * A number. Whole numbers are written as they are, other numbers with two decimals.
     *
     * @param key   placeholder key
     * @param value number to insert
     * @return placeholder
     */
    public static TagResolver.Single number(String key, Number value) {
        return text(key, format(value));
    }

    /**
     * An amount of money, formatted by the economy.
     *
     * @param key     placeholder key
     * @param economy economy formatting the amount
     * @param amount  amount to insert
     * @return placeholder
     */
    public static TagResolver.Single money(String key, Economy economy, double amount) {
        return text(key, economy.format(amount));
    }

    /**
     * The name of an item, showing the item when hovered.
     *
     * @param key   placeholder key
     * @param stack item to insert
     * @return placeholder
     */
    public static TagResolver.Single item(String key, ItemStack stack) {
        return component(key, stack.effectiveName().hoverEvent(stack.asHoverEvent()));
    }

    /**
     * A component inserted as it is.
     *
     * @param key   placeholder key
     * @param value component to insert
     * @return placeholder
     */
    public static TagResolver.Single component(String key, Component value) {
        return Placeholder.component(Keys.require(key), value);
    }

    /**
     * A click action, used as {@code <key>[Button]</key>} around the text that is clicked.
     *
     * @param key   placeholder key
     * @param event click event of the enclosed text
     * @return placeholder
     */
    public static TagResolver.Single click(String key, ClickEvent event) {
        return Placeholder.styling(Keys.require(key), event);
    }

    private static String format(Number value) {
        if (value instanceof Integer
                || value instanceof Long
                || value instanceof Short
                || value instanceof Byte
                || value instanceof BigInteger) {
            return value.toString();
        }
        return String.format(Locale.ROOT, "%.2f", value.doubleValue());
    }
}
