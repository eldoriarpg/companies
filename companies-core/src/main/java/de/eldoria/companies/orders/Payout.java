/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.util.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A payment split into the tax, the company's treasury share and the net amount paid out.
 * <p>
 * The amount after tax is rounded down to two decimals and the tax is the remainder. The treasury share is taken from
 * the amount after tax and rounded down to two decimals; the worker keeps the rest. So {@code net + share + tax}
 * equals the gross amount and a payout never creates money.
 *
 * @param gross amount before tax
 * @param tax   amount taken as tax
 * @param share amount kept in the company treasury
 * @param net   amount paid out
 */
public record Payout(double gross, double tax, double share, double net) {
    /**
     * Splits a gross amount by a tax percentage, without a treasury share.
     *
     * @param gross   amount before tax
     * @param percent tax percentage, clamped to 0 to 100
     * @return the split payout
     */
    public static Payout split(double gross, double percent) {
        return split(gross, percent, 0);
    }

    /**
     * Splits a gross amount by a tax percentage and a treasury share of the amount after tax.
     *
     * @param gross        amount before tax
     * @param taxPercent   tax percentage, clamped to 0 to 100
     * @param sharePercent treasury share in percent of the amount after tax, clamped to 0 to 100
     * @return the split payout
     */
    public static Payout split(double gross, double taxPercent, int sharePercent) {
        var clampedTax = Math.clamp(taxPercent, 0.0, 100.0);
        var exactAfterTax = BigDecimal.valueOf(gross).subtract(Money.percentOf(gross, clampedTax));
        var afterTax = exactAfterTax.setScale(2, RoundingMode.FLOOR).max(BigDecimal.ZERO);
        var tax = BigDecimal.valueOf(gross).subtract(afterTax);
        var share = afterTax.multiply(BigDecimal.valueOf(Math.clamp(sharePercent, 0, 100)))
                .movePointLeft(2)
                .setScale(2, RoundingMode.FLOOR);
        var net = afterTax.subtract(share);
        return new Payout(gross, tax.doubleValue(), share.doubleValue(), net.doubleValue());
    }

    /**
     * The treasury share in whole cents.
     *
     * @return share in cents
     */
    public long shareCents() {
        return Money.toCents(BigDecimal.valueOf(share));
    }
}
