/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

class PriceRaiseTest {
    private static BigDecimal sum(List<Double> shares) {
        return shares.stream().map(BigDecimal::valueOf).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void increaseIsSplitByPrice() {
        Assertions.assertEquals(List.of(75.0, 25.0), PriceRaise.split(List.of(300.0, 100.0), 100));
    }

    @Test
    void freeLinesShareEqually() {
        Assertions.assertEquals(List.of(5.0, 5.0), PriceRaise.split(List.of(0.0, 0.0), 10));
    }

    @Test
    void roundingCentsGoToTheFirstLines() {
        var shares = PriceRaise.split(List.of(1.0, 1.0, 1.0), 1);

        Assertions.assertEquals(List.of(0.34, 0.33, 0.33), shares);
    }

    @Test
    void singleLineGetsEverything() {
        Assertions.assertEquals(List.of(12.34), PriceRaise.split(List.of(5.0), 12.345));
    }

    @Test
    void sharesAlwaysAddUpToTheIncrease() {
        var random = new Random(42);
        for (var run = 0; run < 1000; run++) {
            var prices =
                    random.doubles(1 + random.nextInt(5), 0, 10_000).boxed().toList();
            var increase = BigDecimal.valueOf(random.nextInt(1_000_000)).movePointLeft(2);

            var shares = PriceRaise.split(prices, increase.doubleValue());

            Assertions.assertEquals(0, increase.compareTo(sum(shares)), () -> prices + " " + increase);
            Assertions.assertTrue(shares.stream().allMatch(share -> share >= 0));
        }
    }
}
