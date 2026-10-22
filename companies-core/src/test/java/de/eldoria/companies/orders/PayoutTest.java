/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
}
