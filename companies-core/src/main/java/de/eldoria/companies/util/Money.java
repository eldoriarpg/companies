/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import de.eldoria.eldoutilities.localization.MessageComposer;
import net.milkbowl.vault.economy.Economy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rounding of money amounts. Amounts are rounded down, so rounding never creates money.
 * <p>
 * The treasury stores money as whole cents. Amounts enter it as values with at most two decimals, so converting them
 * to cents and back is exact.
 */
public final class Money {
    private Money() {}

    /**
     * Rounds an amount down to two decimals.
     *
     * @param amount amount to round
     * @return the amount rounded towards negative infinity at the second decimal
     */
    public static double floor(double amount) {
        return floorDecimal(amount).doubleValue();
    }

    /**
     * Rounds an amount down to two decimals.
     *
     * @param amount amount to round
     * @return the amount as decimal rounded towards negative infinity at the second decimal
     */
    public static BigDecimal floorDecimal(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.FLOOR);
    }

    /**
     * The share of an amount described by a percentage.
     *
     * @param amount  amount to take the share of
     * @param percent percentage, where 5 means 5 %
     * @return the exact share
     */
    public static BigDecimal percentOf(double amount, double percent) {
        return BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(percent)).movePointLeft(2);
    }

    /**
     * Converts an amount with at most two decimals to whole cents.
     *
     * @param amount amount to convert
     * @return the amount in cents
     * @throws ArithmeticException when the amount has more than two decimals or does not fit into a long
     */
    public static long toCents(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }

    /**
     * Converts an amount to whole cents, rounding it down to two decimals first.
     *
     * @param amount amount to convert
     * @return the amount rounded down in cents
     */
    public static long floorCents(double amount) {
        return toCents(floorDecimal(amount));
    }

    /**
     * Converts whole cents to an amount with two decimals.
     *
     * @param cents amount in cents
     * @return the amount
     */
    public static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    /**
     * The name of the currency for messages: the plural, else the singular, else the localized word for money.
     *
     * @param economy economy providing the names
     * @return currency name or locale tag
     */
    public static String currencyName(Economy economy) {
        if (!economy.currencyNamePlural().isBlank()) return economy.currencyNamePlural();
        if (!economy.currencyNameSingular().isBlank()) return economy.currencyNameSingular();
        return MessageComposer.escape("words.money");
    }
}
