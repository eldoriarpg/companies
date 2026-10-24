/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Texts;
import net.milkbowl.vault.economy.Economy;

/**
 * The money lines of order views. Owners see the fee and the total they paid, companies see the tax and what is
 * left for their workers; neither side sees the other's numbers.
 */
public final class PriceLines {
    private PriceLines() {}

    /**
     * Adds the fee and the total below the price, when there is a fee, as named placeholders {@code fee} and
     * {@code total}.
     *
     * @param text    text ending with the price line
     * @param economy economy formatting the amounts
     * @param price   price of the order
     * @param fee     fee of the order
     * @return the text
     */
    public static MenuText fee(MenuText text, Economy economy, double price, double fee) {
        if (!(fee > 0)) return text;
        return text.newLine()
                .text("<name>")
                .locale("words.fee")
                .text(": <value><fee> <inactive>(", Values.money("fee", economy, fee))
                .locale("order.fee.nonRefundable")
                .text(")")
                .newLine()
                .text("<name>")
                .locale("words.total")
                .text(": <value><total>", Values.money("total", economy, price + fee));
    }

    /**
     * Adds the tax and the net payout below the price, when the company pays tax, as named placeholders
     * {@code order_tax}, {@code tax_percent} and {@code order_net}.
     * <p>
     * The values are for the whole price; the payout rounds each worker's share separately.
     *
     * @param text       text ending with the price line
     * @param economy    economy formatting the amounts
     * @param price      price of the order
     * @param taxPercent tax of the company, where 5 means 5 %
     * @return the text
     */
    public static MenuText tax(MenuText text, Economy economy, double price, double taxPercent) {
        var payout = Payout.split(price, taxPercent);
        if (!(payout.tax() > 0)) return text;
        return text.newLine()
                .text(
                        "<name><i18n:words.tax>: <value><order_tax> (<tax_percent> %)",
                        Values.money("order_tax", economy, payout.tax()),
                        Values.text("tax_percent", Texts.percent(taxPercent)))
                .newLine()
                .text("<name><i18n:words.net>: <value><order_net>", Values.money("order_net", economy, payout.net()));
    }
}
