/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.order.MaterialPrice;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderProvidersTest {
    private static OwnOrder order(int id, OrderState state) {
        return new OwnOrder(id, state, Component.text("order " + id));
    }

    @Test
    void lineNumbersStartAtOneAndMatchTheTypedDigits() {
        assertEquals(List.of(1, 2, 3), OrderProviders.lineNumbers(3, ""));
        assertEquals(List.of(1, 10, 11), OrderProviders.lineNumbers(11, "1"));
        assertEquals(List.of(), OrderProviders.lineNumbers(0, ""));
    }

    @Test
    void ownOrdersAreFilteredByStateAndTypedId() {
        var orders = List.of(
                order(12, OrderState.UNCLAIMED), order(13, OrderState.DELIVERED), order(21, OrderState.UNCLAIMED));

        assertEquals(
                List.of(12, 21),
                OrderProviders.ownOrders(orders, EnumSet.of(OrderState.UNCLAIMED), "").stream()
                        .map(OwnOrder::id)
                        .toList());
        assertEquals(
                List.of(12),
                OrderProviders.ownOrders(orders, EnumSet.of(OrderState.UNCLAIMED), "1").stream()
                        .map(OwnOrder::id)
                        .toList());
        assertEquals(
                List.of(13),
                OrderProviders.ownOrders(orders, EnumSet.of(OrderState.DELIVERED), "").stream()
                        .map(OwnOrder::id)
                        .toList());
    }

    @Test
    void theMarketAverageIsSuggestedAsTypeableNumber() {
        assertEquals(Optional.of("2.5"), OrderProviders.unitPrice(false, new MaterialPrice("stone", 2.5, 1, 3)));
        assertEquals(Optional.of("0.33"), OrderProviders.unitPrice(false, new MaterialPrice("stone", 1.0 / 3, 0, 1)));
        assertEquals(Optional.of("12"), OrderProviders.unitPrice(false, new MaterialPrice("stone", 12.0, 0, 20)));
    }

    @Test
    void exactItemsAndMaterialsWithoutDataGetNoMarketAverage() {
        assertEquals(Optional.empty(), OrderProviders.unitPrice(true, new MaterialPrice("stone", 2.5, 1, 3)));
        assertEquals(Optional.empty(), OrderProviders.unitPrice(false, new MaterialPrice("stone")));
    }
}
