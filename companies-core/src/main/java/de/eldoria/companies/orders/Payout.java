/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.util.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A payment split into the tax and the net amount paid out.
 * <p>
 * The net amount is rounded down to two decimals and the tax is the remainder, so {@code net + tax} equals the gross
 * amount and a payout never exceeds it.
 *
 * @param gross amount before tax
 * @param tax   amount taken as tax
 * @param net   amount paid out
 */
public record Payout(double gross, double tax, double net) {
    /**
     * Splits a gross amount by a tax percentage.
     *
     * @param gross   amount before tax
     * @param percent tax percentage, clamped to 0 to 100
     * @return the split payout
     */
    public static Payout split(double gross, double percent) {
        var clamped = Math.clamp(percent, 0.0, 100.0);
        var exactNet = BigDecimal.valueOf(gross).subtract(Money.percentOf(gross, clamped));
        var net = exactNet.setScale(2, RoundingMode.FLOOR).max(BigDecimal.ZERO);
        var tax = BigDecimal.valueOf(gross).subtract(net);
        return new Payout(gross, tax.doubleValue(), net.doubleValue());
    }
}
