/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.order;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Texts;
import net.milkbowl.vault.economy.Economy;

import java.time.Duration;

/**
 * Text blocks shared by the order views of owners and companies.
 * <p>
 * Blocks that appear more than once in one text take a key prefix, such as {@code order_2}, so their placeholder keys
 * stay unique. Item names are inserted as components, so an exact line shows its real name and the item when hovered.
 */
public final class OrderTexts {
    private OrderTexts() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * The id and the name of an order, as {@code order_id} and {@code order_name}.
     *
     * @param prefix key prefix, or empty for a text showing one order
     * @param id     id of the order
     * @param name   name of the order
     * @return the text
     */
    public static MenuText title(String prefix, int id, String name) {
        var idKey = key(prefix, "order_id");
        var nameKey = key(prefix, "order_name");
        return MenuText.create()
                .text(
                        "<name><" + idKey + "> | <value><" + nameKey + ">",
                        Values.number(idKey, id),
                        Values.text(nameKey, name));
    }

    /**
     * @param state state of the order
     * @return the status line
     */
    public static MenuText status(OrderState state) {
        return MenuText.create().text("<name><i18n:words.status>: <value><i18n:" + state.translationKey() + ">");
    }

    /**
     * A line with its requested amount and price, as an order nobody works on shows it.
     *
     * @param prefix  key prefix, or empty for a text showing one order
     * @param line    line to show
     * @param economy economy formatting the price
     * @return the text
     */
    public static MenuText requested(String prefix, OrderLineView line, Economy economy) {
        var item = lineKey(prefix, line, "item");
        var amount = lineKey(prefix, line, "amount");
        var price = lineKey(prefix, line, "price");
        return MenuText.create()
                .text(
                        "<yellow><" + item + "> <blue><" + amount + ">x <gold><" + price + ">",
                        Values.component(item, line.itemName()),
                        Values.number(amount, line.amount()),
                        Values.money(price, economy, line.price()));
    }

    /**
     * A line with its delivered and requested amount and its price.
     *
     * @param prefix  key prefix, or empty for a text showing one order
     * @param line    line to show
     * @param economy economy formatting the price
     * @return the text
     */
    public static MenuText progress(String prefix, OrderLineView line, Economy economy) {
        var item = lineKey(prefix, line, "item");
        var delivered = lineKey(prefix, line, "delivered");
        var amount = lineKey(prefix, line, "amount");
        var price = lineKey(prefix, line, "price");
        return MenuText.create()
                .text(
                        "<yellow><" + item + "> <blue><" + delivered + ">/<" + amount + "> <gold><" + price + ">",
                        Values.component(item, line.itemName()),
                        Values.number(delivered, line.delivered()),
                        Values.number(amount, line.amount()),
                        Values.money(price, economy, line.price()));
    }

    /**
     * @param line line to show
     * @return progress bar of the line
     */
    public static MenuText progressBar(OrderLineView line) {
        return MenuText.create().text(Texts.progressBar(line.progress()));
    }

    /**
     * How many delivered items of a line are damaged and the wear of the most worn one. Empty when none is damaged.
     *
     * @param line line to show
     * @return the text
     */
    public static MenuText damage(OrderLineView line) {
        if (line.damaged() <= 0) return MenuText.create();
        var damaged = lineKey("", line, "damaged");
        var wear = lineKey("", line, "wear");
        return MenuText.create()
                .text(
                        "<inactive><i18n:company.order.info.damaged>: <value><" + damaged + "> <inactive>(<" + wear
                                + "> %)",
                        Values.number(damaged, line.damaged()),
                        Values.number(wear, line.worstDamagePercent()));
    }

    /**
     * @param prefix  key prefix, or empty for a text showing one order
     * @param price   price of the order
     * @param economy economy formatting the price
     * @return the price line
     */
    public static MenuText price(String prefix, double price, Economy economy) {
        var key = key(prefix, "order_price");
        return MenuText.create()
                .text("<name><i18n:words.price>: <value><" + key + ">", Values.money(key, economy, price));
    }

    /**
     * A locale text with the time left as {@code duration}.
     *
     * @param localeKey locale text using {@code <duration>}
     * @param left      time left
     * @return the text
     */
    public static MenuText timeLeft(String localeKey, Duration left) {
        return MenuText.create().text("<name>").locale(localeKey, Values.text("duration", pretty(left)));
    }

    /**
     * @param left time left
     * @return the time left, never below zero
     */
    public static String pretty(Duration left) {
        return Texts.prettyDuration(left.isNegative() ? Duration.ZERO : left);
    }

    private static String lineKey(String prefix, OrderLineView line, String part) {
        return key(prefix, Keys.of("line", line.number(), part));
    }

    private static String key(String prefix, String key) {
        if (prefix.isEmpty()) return Keys.require(key);
        return Keys.of(prefix, key);
    }
}
