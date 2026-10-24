/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.util.Money;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

class PayoutTest {
    @Test
    void zeroTaxPaysTheGrossRoundedDown() {
        var payout = Payout.split(33.333333, 0);

        Assertions.assertEquals(33.33, payout.net());
        Assertions.assertEquals(0.003333, payout.tax(), 1e-9);
    }

    @Test
    void taxIsTheRemainderOfTheRoundedDownNet() {
        var payout = Payout.split(99.99, 7.5);

        Assertions.assertEquals(92.49, payout.net());
        Assertions.assertEquals(7.5, payout.tax(), 1e-9);
    }

    @Test
    void fullTaxPaysNothing() {
        var payout = Payout.split(120, 100);

        Assertions.assertEquals(0.0, payout.net());
        Assertions.assertEquals(120.0, payout.tax());
    }

    @Test
    void taxOutsideTheRangeIsClamped() {
        Assertions.assertEquals(50.0, Payout.split(50, -10).net());
        Assertions.assertEquals(0.0, Payout.split(50, 250).net());
    }

    @Test
    void payoutsNeverExceedTheGross() {
        var random = new Random(17);
        for (var i = 0; i < 10_000; i++) {
            var gross = random.nextInt(10_000_000) / 100.0 + random.nextDouble();
            var percent = random.nextInt(10_000) / 100.0;

            var payout = Payout.split(gross, percent);

            Assertions.assertTrue(payout.net() >= 0);
            Assertions.assertTrue(payout.tax() >= 0);
            Assertions.assertTrue(
                    BigDecimal.valueOf(payout.net()).compareTo(BigDecimal.valueOf(gross)) <= 0,
                    () -> gross + " at " + percent + "% paid " + payout.net());
            Assertions.assertEquals(gross, payout.net() + payout.tax(), 1e-6);
        }
    }

    @Test
    void sumOfWorkerPayoutsNeverExceedsThePrice() {
        var random = new Random(42);
        for (var order = 0; order < 1000; order++) {
            var workers = 1 + random.nextInt(6);
            var price = BigDecimal.ZERO;
            var paid = BigDecimal.ZERO;
            var percent = random.nextInt(3000) / 100.0;
            for (var worker = 0; worker < workers; worker++) {
                var gross = random.nextInt(1_000_000) / 100.0;
                price = price.add(BigDecimal.valueOf(gross));
                paid = paid.add(BigDecimal.valueOf(Payout.split(gross, percent).net()));
            }
            Assertions.assertTrue(paid.compareTo(price) <= 0);
        }
    }

    @Test
    void shareIsTakenFromTheAmountAfterTaxRoundedDown() {
        var payout = Payout.split(99.99, 7.5, 15);

        Assertions.assertEquals(7.5, payout.tax(), 1e-9);
        Assertions.assertEquals(13.87, payout.share());
        Assertions.assertEquals(78.62, payout.net());
        Assertions.assertEquals(1387L, payout.shareCents());
    }

    @Test
    void shareOutsideTheRangeIsClamped() {
        Assertions.assertEquals(0.0, Payout.split(50, 0, -5).share());
        Assertions.assertEquals(50.0, Payout.split(50, 0, 250).share());
        Assertions.assertEquals(0.0, Payout.split(50, 0, 250).net());
    }

    @Test
    void zeroShareEqualsTheSplitWithoutShare() {
        var random = new Random(7);
        for (var i = 0; i < 10_000; i++) {
            var gross = random.nextInt(10_000_000) / 100.0 + random.nextDouble();
            var percent = random.nextInt(10_000) / 100.0;

            var withoutShare = Payout.split(gross, percent);
            var zeroShare = Payout.split(gross, percent, 0);

            Assertions.assertEquals(withoutShare, zeroShare);
            Assertions.assertEquals(0.0, zeroShare.share());
        }
    }

    @Test
    void partsAddUpToTheGrossExactly() {
        var random = new Random(23);
        for (var i = 0; i < 10_000; i++) {
            var gross = random.nextInt(100_000_000) / 100.0;
            var taxPercent = random.nextInt(10_000) / 100.0;
            var sharePercent = random.nextInt(101);

            var payout = Payout.split(gross, taxPercent, sharePercent);

            var sum = BigDecimal.valueOf(payout.net())
                    .add(BigDecimal.valueOf(payout.share()))
                    .add(BigDecimal.valueOf(payout.tax()));
            Assertions.assertEquals(
                    0,
                    sum.compareTo(BigDecimal.valueOf(gross)),
                    () -> gross + " at " + taxPercent + "% tax and " + sharePercent + "% share split into " + payout);
            Assertions.assertTrue(payout.net() >= 0);
            Assertions.assertTrue(payout.share() >= 0);
            Assertions.assertTrue(payout.tax() >= 0);
            Assertions.assertEquals(
                    BigDecimal.valueOf(payout.share()).setScale(2, RoundingMode.UNNECESSARY),
                    Money.fromCents(payout.shareCents()));
        }
    }

    @Test
    void shareNeverExceedsItsPercentOfTheAmountAfterTax() {
        var random = new Random(31);
        for (var i = 0; i < 10_000; i++) {
            var gross = random.nextInt(10_000_000) / 100.0 + random.nextDouble();
            var taxPercent = random.nextInt(10_000) / 100.0;
            var sharePercent = random.nextInt(101);

            var payout = Payout.split(gross, taxPercent, sharePercent);

            var afterTax = BigDecimal.valueOf(payout.net()).add(BigDecimal.valueOf(payout.share()));
            var exactShare = afterTax.multiply(BigDecimal.valueOf(sharePercent)).movePointLeft(2);
            Assertions.assertTrue(BigDecimal.valueOf(payout.share()).compareTo(exactShare) <= 0);
            Assertions.assertTrue(
                    exactShare.subtract(BigDecimal.valueOf(payout.share())).compareTo(new BigDecimal("0.01")) < 0);
            Assertions.assertEquals(Payout.split(gross, taxPercent).net(), afterTax.doubleValue());
        }
    }
}
