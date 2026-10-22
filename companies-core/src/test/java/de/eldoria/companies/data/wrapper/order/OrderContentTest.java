/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.orders.PaymentType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

class OrderContentTest {
    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();

    private OrderContent content(int amount, double price, ContentPart... parts) {
        var content = new OrderContent(new ItemStack(Material.STONE), amount, price);
        content.parts(List.of(parts));
        return content;
    }

    private FullOrder order(OrderContent... contents) {
        return new FullOrder(1, UUID.randomUUID(), "order", LocalDateTime.now(), 1, LocalDateTime.now(), OrderState.CLAIMED,
                List.of(contents));
    }

    @Test
    void paymentsSumPartsOfTheSameWorker() {
        var content = content(10, 100.0, new ContentPart(first, 3), new ContentPart(first, 2), new ContentPart(second, 5));

        var payments = content.payments();

        Assertions.assertEquals(50.0, payments.get(first), 0.0001);
        Assertions.assertEquals(50.0, payments.get(second), 0.0001);
        Assertions.assertEquals(5, content.workerAmount().get(first));
    }

    @Test
    void totalPaymentsSumPartsOfTheSameWorker() {
        var order = order(content(10, 100.0, new ContentPart(first, 3), new ContentPart(first, 7)),
                content(10, 50.0, new ContentPart(second, 10)));

        var payments = order.payments(PaymentType.TOTAL);

        Assertions.assertEquals(75.0, payments.get(first), 0.0001);
        Assertions.assertEquals(75.0, payments.get(second), 0.0001);
    }

    @Test
    void orderIsDoneWhenAllItemsAreDelivered() {
        Assertions.assertFalse(order(content(10, 1.0, new ContentPart(first, 9))).isDone());
        Assertions.assertTrue(order(content(10, 1.0, new ContentPart(first, 10))).isDone());
        Assertions.assertTrue(order(content(10, 1.0, new ContentPart(first, 6), new ContentPart(second, 6))).isDone());
    }

    @Test
    void progressIsTheDeliveredShare() {
        Assertions.assertEquals(0.0, order(content(10, 1.0)).progress(), 0.0001);
        Assertions.assertEquals(0.25, order(content(10, 1.0, new ContentPart(first, 2)), content(10, 1.0, new ContentPart(second, 3))).progress(), 0.0001);
        Assertions.assertEquals(1.0, order(content(10, 1.0, new ContentPart(first, 10))).progress(), 0.0001);
    }

    @Test
    void progressOfAnEmptyOrderIsZero() {
        Assertions.assertEquals(0.0, order().progress());
        Assertions.assertEquals(0.0, order(content(0, 1.0)).progress());
    }
}
