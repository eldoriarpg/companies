/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

import de.eldoria.companies.components.order.OrderState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

class SimpleOrderTest {
    private static SimpleOrder order(int extensions) {
        return new SimpleOrder(
                1,
                UUID.randomUUID(),
                "order",
                LocalDateTime.now(),
                0,
                LocalDateTime.now(),
                OrderState.UNCLAIMED,
                0.0,
                extensions,
                0);
    }

    @Test
    void extensionsLeftCountDown() {
        Assertions.assertEquals(2, order(0).extensionsLeft(2));
        Assertions.assertEquals(1, order(1).extensionsLeft(2));
        Assertions.assertEquals(0, order(2).extensionsLeft(2));
    }

    @Test
    void loweredLimitLeavesNoExtensions() {
        Assertions.assertEquals(0, order(2).extensionsLeft(1));
    }

    @Test
    void zeroDisablesExtending() {
        Assertions.assertEquals(0, order(0).extensionsLeft(0));
    }
}
