/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.order.Claimant;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.orders.OrderTable;
import de.eldoria.companies.util.TestExecutors;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

class SqLiteSoloOrderDataTest {
    private static final int COMPANY = 3;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @TempDir
    Path directory;

    private OrderTable orders;
    private SqLiteOrderData orderData;

    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        orders = OrderTable.create();
        orderData = orders.orderData();
    }

    private boolean claim(UUID player, SimpleOrder order, int maxActive) {
        return orderData.submitSoloClaim(player, order, maxActive).join();
    }

    private static void ageClaim(SimpleOrder order, int hours) {
        query("UPDATE order_states SET last_update = datetime(CURRENT_TIMESTAMP, '-' || ? || ' HOUR') WHERE id = ?")
                .single(call().bind(hours).bind(order.id()))
                .update();
    }

    @Test
    void aSoloClaimNamesThePlayerAndNoCompany() {
        var order = orders.place(0.0);

        Assertions.assertTrue(claim(alice, order, 1));

        var claimed = orders.reload(order);
        Assertions.assertEquals(OrderState.CLAIMED, claimed.state());
        Assertions.assertEquals(Optional.of(alice), claimed.soloWorker());
        Assertions.assertTrue(claimed.company() < 1);
        Assertions.assertEquals(Optional.of(new Claimant.Solo(alice)), claimed.claimant());
        Assertions.assertEquals(Optional.of(alice), orders.full(order).soloWorker());
    }

    @Test
    void companyClaimsHaveNoSoloWorker() {
        var order = orders.place(0.0);
        orders.state(order, OrderState.CLAIMED, COMPANY);

        var claimed = orders.reload(order);
        Assertions.assertEquals(Optional.empty(), claimed.soloWorker());
        Assertions.assertEquals(Optional.of(new Claimant.Company(COMPANY)), claimed.claimant());
        Assertions.assertEquals(Optional.empty(), orders.place(0.0).claimant());
    }

    @Test
    void aClaimedOrderIsNotClaimedAgain() {
        var order = orders.place(0.0);
        Assertions.assertTrue(claim(alice, order, 1));

        Assertions.assertFalse(claim(bob, orders.reload(order), 1));
        Assertions.assertEquals(Optional.of(alice), orders.reload(order).soloWorker());
    }

    @Test
    void aPlayerAtTheLimitClaimsNoMoreOrders() {
        var first = orders.place(0.0);
        var second = orders.place(0.0);
        var third = orders.place(0.0);

        Assertions.assertTrue(claim(alice, first, 2));
        Assertions.assertTrue(claim(alice, second, 2));
        Assertions.assertFalse(claim(alice, third, 2));
        Assertions.assertEquals(OrderState.UNCLAIMED, orders.reload(third).state());
        Assertions.assertTrue(claim(bob, third, 2));
    }

    @Test
    void parallelClaimsOfOnePlayerRespectTheLimit() throws Exception {
        List<SimpleOrder> open = new ArrayList<>();
        for (var i = 0; i < 6; i++) open.add(orders.place(0.0));
        var executor = Executors.newFixedThreadPool(6);
        try {
            var start = new CountDownLatch(1);
            List<Future<Boolean>> claims = new ArrayList<>();
            for (var order : open) {
                claims.add(executor.submit(() -> {
                    start.await();
                    return claim(alice, order, 2);
                }));
            }
            start.countDown();
            var claimed = 0;
            for (var future : claims) if (future.get(30, TimeUnit.SECONDS)) claimed++;

            Assertions.assertEquals(2, claimed);
            Assertions.assertEquals(2, orderData.retrieveSoloOrderCount(alice).join());
        } finally {
            TestExecutors.drain(executor);
        }
    }

    @Test
    void theClaimLockLeavesNoCooldown() {
        Assertions.assertTrue(claim(alice, orders.place(0.0), 1));

        var stored = orderData.retrieveSoloCooldown(alice).join();
        Assertions.assertTrue(stored.isEmpty() || stored.get().isBefore(LocalDateTime.now()));
    }

    @Test
    void deliveredSoloOrdersDoNotCountForTheLimit() {
        var first = orders.place(0.0);
        var second = orders.place(0.0);
        Assertions.assertTrue(claim(alice, first, 1));
        SqLiteTestDatabase.setState(first.id(), OrderState.DELIVERED.stateId(), 0);

        Assertions.assertTrue(claim(alice, second, 1));
    }

    @Test
    void soloOrdersAreListedAndCountedPerPlayer() {
        var first = orders.place(0.0);
        var second = orders.place(0.0);
        var companyOrder = orders.place(0.0);
        claim(alice, first, 5);
        claim(bob, second, 5);
        orders.state(companyOrder, OrderState.CLAIMED, COMPANY);

        var listed = orderData
                .retrieveOrdersBySoloWorker(alice, OrderState.CLAIMED, OrderState.CLAIMED)
                .join();
        Assertions.assertEquals(
                List.of(first.id()), listed.stream().map(SimpleOrder::id).toList());
        Assertions.assertEquals(1, orderData.retrieveSoloOrderCount(alice).join());
        Assertions.assertEquals(1, orderData.retrieveSoloOrderCount(bob).join());
        Assertions.assertEquals(
                0, orderData.retrieveSoloOrderCount(UUID.randomUUID()).join());
        Assertions.assertEquals(
                List.of(companyOrder.id()),
                orderData.retrieveCompanyOrderById(companyOrder.id(), COMPANY).join().stream()
                        .map(SimpleOrder::id)
                        .toList());
        Assertions.assertTrue(
                orderData.retrieveCompanyOrderById(first.id(), 0).join().isEmpty());
    }

    @Test
    void expiredSoloClaimsAreFoundApartFromCompanyClaims() {
        var solo = orders.place(0.0);
        var fresh = orders.place(0.0);
        var company = orders.place(0.0);
        claim(alice, solo, 5);
        claim(alice, fresh, 5);
        orders.state(company, OrderState.CLAIMED, COMPANY);
        ageClaim(solo, 30);
        ageClaim(company, 30);

        var expiredSolo = orderData.retrieveExpiredSoloOrders(24).join();
        var expiredCompany = orderData.retrieveExpiredOrders(24).join();

        Assertions.assertEquals(
                List.of(solo.id()), expiredSolo.stream().map(SimpleOrder::id).toList());
        Assertions.assertEquals(
                List.of(company.id()),
                expiredCompany.stream().map(SimpleOrder::id).toList());
    }

    @Test
    void anAdminExtensionDelaysTheSoloDeadline() {
        var order = orders.place(0.0);
        claim(alice, order, 1);
        ageClaim(order, 30);
        orderData.submitClaimExtension(orders.reload(order), 10).join();

        Assertions.assertTrue(orderData.retrieveExpiredSoloOrders(24).join().isEmpty());
    }

    @Test
    void releasingASoloClaimReturnsTheItemsAndForgetsTheWorker() {
        var order = orders.place(0.0);
        claim(alice, order, 1);
        orders.deliver(order, 0, Material.STONE, alice, 4);

        var returned = orderData.submitUnclaimOrder(orders.reload(order)).join().orElseThrow();

        Assertions.assertEquals(Set.of(alice), returned.workers());
        Assertions.assertEquals(4, returned.byWorker().get(alice).get(0).amount());
        var released = orders.reload(order);
        Assertions.assertEquals(OrderState.UNCLAIMED, released.state());
        Assertions.assertEquals(Optional.empty(), released.soloWorker());
        Assertions.assertEquals(0, orderData.retrieveSoloOrderCount(alice).join());
    }

    @Test
    void deliveredAndReceivedSoloOrdersCountAsCompleted() {
        var claimed = orders.place(0.0);
        var delivered = orders.place(0.0);
        var received = orders.place(0.0);
        claim(alice, claimed, 5);
        claim(alice, delivered, 5);
        claim(alice, received, 5);
        SqLiteTestDatabase.setState(delivered.id(), OrderState.DELIVERED.stateId(), 0);
        SqLiteTestDatabase.setState(received.id(), OrderState.RECEIVED.stateId(), 0);

        Assertions.assertEquals(2, orderData.retrieveSoloCompletedCount(alice).join());
        Assertions.assertEquals(1, orderData.retrieveSoloOrderCount(alice).join());
        Assertions.assertEquals(0, orderData.retrieveSoloCompletedCount(bob).join());
    }

    @Test
    void cooldownsAreStoredAndReplaced() {
        var first = LocalDateTime.of(2026, 5, 1, 12, 30, 0);
        var second = LocalDateTime.of(2026, 5, 2, 8, 0, 0);

        Assertions.assertEquals(
                Optional.empty(), orderData.retrieveSoloCooldown(alice).join());
        Assertions.assertTrue(orderData.submitSoloCooldown(alice, first).join());
        Assertions.assertEquals(
                Optional.of(first), orderData.retrieveSoloCooldown(alice).join());
        Assertions.assertTrue(orderData.submitSoloCooldown(alice, second).join());
        Assertions.assertEquals(
                Optional.of(second), orderData.retrieveSoloCooldown(alice).join());
        Assertions.assertEquals(
                Optional.empty(), orderData.retrieveSoloCooldown(bob).join());
    }
}
