/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.ordersettings;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OrderFeeTest {
    @Test
    void defaultConfigurationChargesNothing() {
        Assertions.assertEquals(0.0, new OrderFee().of(12_345.67));
    }

    @Test
    void feeIsFixedPlusPercentOfThePrice() {
        var fee = new OrderFee(10, 5, 0, 0);

        Assertions.assertEquals(60.0, fee.of(1000));
        Assertions.assertEquals(10.0, fee.of(0));
    }

    @Test
    void minimumRaisesSmallFees() {
        var fee = new OrderFee(0, 1, 25, 0);

        Assertions.assertEquals(25.0, fee.of(100));
        Assertions.assertEquals(30.0, fee.of(3000));
    }

    @Test
    void maximumCapsLargeFees() {
        var fee = new OrderFee(0, 10, 0, 500);

        Assertions.assertEquals(100.0, fee.of(1000));
        Assertions.assertEquals(500.0, fee.of(1_000_000));
    }

    @Test
    void minimumAloneIsCharged() {
        Assertions.assertEquals(5.0, new OrderFee(0, 0, 5, 0).of(100));
    }

    @Test
    void feeIsRoundedDown() {
        var fee = new OrderFee(0, 3, 0, 0);

        Assertions.assertEquals(0.29, fee.of(9.99));
        Assertions.assertEquals(0.03, fee.of(1.13));
    }

    @Test
    void negativeFeesAreZero() {
        Assertions.assertEquals(0.0, new OrderFee(-50, 1, 0, 0).of(100));
    }

    @Test
    void raiseChargesThePercentOfTheIncreaseOnly() {
        var fee = new OrderFee(10, 5, 0, 0);

        Assertions.assertEquals(5.0, fee.raise(60, 100));
    }

    @Test
    void raiseIsCappedByTheMaximumOfTheTotalFee() {
        var fee = new OrderFee(0, 10, 0, 50);

        Assertions.assertEquals(10.0, fee.raise(40, 1000));
        Assertions.assertEquals(0.0, fee.raise(50, 1000));
    }

    @Test
    void raiseTopsUpToTheMinimumOfTheTotalFee() {
        var fee = new OrderFee(0, 1, 25, 0);

        Assertions.assertEquals(20.0, fee.raise(5, 100));
    }

    @Test
    void raiseIsRoundedDownAndNeverNegative() {
        var fee = new OrderFee(0, 3, 0, 0);

        Assertions.assertEquals(0.29, fee.raise(0.1, 9.99));
        Assertions.assertEquals(0.0, fee.raise(10, 0));
        Assertions.assertEquals(0.0, new OrderFee(0, 0, 0, 5).raise(10, 100));
    }
}
