/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import de.eldoria.companies.configuration.elements.systemorders.RulePrice;
import de.eldoria.companies.configuration.elements.systemorders.RulePrice.PriceMode;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulePricingTest {
    private static final OptionalDouble NONE = OptionalDouble.empty();

    @Test
    void aFixedPriceWithoutJitterIsPaidAsItIs() {
        var priced = RulePricing.price(new RulePrice(PriceMode.FIXED, 2.5, 1, 0, 0, 0), NONE, () -> 0.9)
                .orElseThrow();

        assertEquals(2.5, priced.unit());
        assertNull(priced.average());
        assertFalse(priced.bounded());
    }

    @Test
    void anAveragePriceIsTheMarketAverageTimesTheFactor() {
        var priced = RulePricing.price(
                        new RulePrice(PriceMode.AVERAGE, 0, 1.2, 0, 0, 0), OptionalDouble.of(2.0), () -> 0.5)
                .orElseThrow();

        assertEquals(2.0, priced.average());
        assertEquals(2.4, priced.unit(), 0.000_1);
    }

    @Test
    void anAveragePriceWithoutMarketDataHasNoPrice() {
        assertTrue(RulePricing.price(new RulePrice(PriceMode.AVERAGE, 0, 1, 0, 0, 0), NONE, () -> 0.5)
                .isEmpty());
        assertTrue(RulePricing.price(new RulePrice(PriceMode.AVERAGE, 0, 1, 0, 0, 0), OptionalDouble.of(0), () -> 0.5)
                .isEmpty());
    }

    @Test
    void theJitterMovesThePriceEitherWayWithinItsShare() {
        var price = new RulePrice(PriceMode.FIXED, 10, 1, 0.1, 0, 0);

        assertEquals(
                9.0, RulePricing.price(price, NONE, () -> 0.0).orElseThrow().unit());
        assertEquals(
                10.0, RulePricing.price(price, NONE, () -> 0.5).orElseThrow().unit());
        assertEquals(
                10.8, RulePricing.price(price, NONE, () -> 0.9).orElseThrow().unit());
        assertEquals(
                -0.1, RulePricing.price(price, NONE, () -> 0.0).orElseThrow().jitter(), 0.000_1);
    }

    @Test
    void theBoundsKeepThePriceInside() {
        var low = RulePricing.price(new RulePrice(PriceMode.FIXED, 1, 1, 0, 2, 5), NONE, () -> 0.5)
                .orElseThrow();
        var high = RulePricing.price(new RulePrice(PriceMode.FIXED, 9, 1, 0, 2, 5), NONE, () -> 0.5)
                .orElseThrow();

        assertEquals(2.0, low.unit());
        assertTrue(low.bounded());
        assertEquals(5.0, high.unit());
        assertTrue(high.bounded());
    }

    @Test
    void thePriceIsRoundedToCentsAndMustBePositive() {
        assertEquals(
                1.23,
                RulePricing.price(new RulePrice(PriceMode.FIXED, 1.2345, 1, 0, 0, 0), NONE, () -> 0.5)
                        .orElseThrow()
                        .unit());
        assertTrue(RulePricing.price(new RulePrice(PriceMode.FIXED, 0.001, 1, 0, 0, 0), NONE, () -> 0.5)
                .isEmpty());
    }
}
