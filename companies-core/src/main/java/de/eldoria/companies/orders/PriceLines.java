/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.util.Texts;
import de.eldoria.eldoutilities.localization.MessageComposer;
import net.milkbowl.vault.economy.Economy;

/**
 * The money lines of order views. Owners see the fee and the total they paid, companies see the tax and what is
 * left for their workers; neither side sees the other's numbers.
 */
public final class PriceLines {
    private PriceLines() {}

    /**
     * Adds the fee and the total below the price, when there is a fee.
     *
     * @param composer composer ending with the price line
     * @param economy  economy formatting the amounts
     * @param price    price of the order
     * @param fee      fee of the order
     * @return the composer
     */
    public static MessageComposer fee(MessageComposer composer, Economy economy, double price, double fee) {
        if (!(fee > 0)) return composer;
        return composer.newLine()
                .text("<name>")
                .localeCode("words.fee")
                .text(": <value>%s <inactive>(", economy.format(fee))
                .localeCode("order.fee.nonRefundable")
                .text(")")
                .newLine()
                .text("<name>")
                .localeCode("words.total")
                .text(": <value>%s", economy.format(price + fee));
    }

    /**
     * Adds the tax and the net payout below the price, when the company pays tax.
     * <p>
     * The values are for the whole price; the payout rounds each worker's share separately.
     *
     * @param composer   composer ending with the price line
     * @param economy    economy formatting the amounts
     * @param price      price of the order
     * @param taxPercent tax of the company, where 5 means 5 %
     * @return the composer
     */
    public static MessageComposer tax(MessageComposer composer, Economy economy, double price, double taxPercent) {
        var payout = Payout.split(price, taxPercent);
        if (!(payout.tax() > 0)) return composer;
        return composer.newLine()
                .text("<name>")
                .localeCode("words.tax")
                .text(": <value>%s (%s %%)", economy.format(payout.tax()), Texts.percent(taxPercent))
                .newLine()
                .text("<name>")
                .localeCode("words.net")
                .text(": <value>%s", economy.format(payout.net()));
    }
}
