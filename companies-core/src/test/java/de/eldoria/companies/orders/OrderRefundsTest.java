/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderRefundsTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void aChargedOrderRefundsItsOwnerAndAnUnchargedOneNothing() {
        List<Double> deposits = new ArrayList<>();
        var refunds = OrderRefunds.of((owner, amount) -> deposits.add(amount), null);

        refunds.refund(order(true), 12.5);
        refunds.refund(order(false), 30.0);

        assertEquals(List.of(12.5), deposits);
    }

    private static SimpleOrder order(boolean charged) {
        var now = LocalDateTime.of(2026, 11, 1, 12, 0);
        return new SimpleOrder(1, OWNER, "test", now, 0, now, OrderState.UNCLAIMED, 2.0, 0, 0, 0, null, charged);
    }
}
