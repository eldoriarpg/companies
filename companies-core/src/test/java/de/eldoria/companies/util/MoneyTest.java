/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Random;

class MoneyTest {
    @Test
    void amountsWithTwoDecimalsConvertToCentsExactly() {
        Assertions.assertEquals(1234L, Money.toCents(new BigDecimal("12.34")));
        Assertions.assertEquals(1200L, Money.toCents(new BigDecimal("12")));
        Assertions.assertEquals(1L, Money.toCents(new BigDecimal("0.01")));
        Assertions.assertEquals(-250L, Money.toCents(new BigDecimal("-2.5")));
        Assertions.assertEquals(0L, Money.toCents(BigDecimal.ZERO));
    }

    @Test
    void moreThanTwoDecimalsAreRefused() {
        Assertions.assertThrows(ArithmeticException.class, () -> Money.toCents(new BigDecimal("0.001")));
        Assertions.assertThrows(ArithmeticException.class, () -> Money.toCents(new BigDecimal("1.005")));
    }

    @Test
    void amountsBeyondALongAreRefused() {
        Assertions.assertThrows(ArithmeticException.class, () -> Money.toCents(new BigDecimal("1E20")));
    }

    @Test
    void centsConvertBackWithTwoDecimals() {
        Assertions.assertEquals(new BigDecimal("12.34"), Money.fromCents(1234));
        Assertions.assertEquals(new BigDecimal("0.05"), Money.fromCents(5));
        Assertions.assertEquals(new BigDecimal("-1.00"), Money.fromCents(-100));
    }

    @Test
    void roundTripIsExactForRandomAmounts() {
        var random = new Random(7);
        for (var i = 0; i < 10_000; i++) {
            var cents = random.nextLong(-1_000_000_000_000L, 1_000_000_000_000L);
            Assertions.assertEquals(cents, Money.toCents(Money.fromCents(cents)));
        }
    }

    @Test
    void flooredAmountsConvertToCents() {
        Assertions.assertEquals(1999L, Money.floorCents(19.999));
        Assertions.assertEquals(1000L, Money.floorCents(10.0));
        Assertions.assertEquals(1L, Money.floorCents(0.019));
        Assertions.assertEquals(-2L, Money.floorCents(-0.011));
    }

    @Test
    void flooredSumsOfCentsMatchTheDecimalSum() {
        var sum = Money.fromCents(Money.floorCents(0.1) + Money.floorCents(0.2));

        Assertions.assertEquals(new BigDecimal("0.30"), sum);
    }
}
