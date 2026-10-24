/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.configuration.elements.ordersettings.OrderFee;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteHistoryData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.EventRecorder;
import de.eldoria.companies.services.ActionResult;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.util.TestExecutors;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class OwnerOrderServiceTest {
    private static final double FEE = 5.0;
    private static final double PRICE = 150.0;

    @TempDir
    Path directory;

    private final Map<UUID, Double> wallets = new ConcurrentHashMap<>();
    private final AtomicReference<Double> collected = new AtomicReference<>(0.0);
    private final List<UUID> changed = new CopyOnWriteArrayList<>();
    private final OrderSettings settings = new OrderSettings() {
        @Override
        public OrderFee fee() {
            return new OrderFee(FEE, 10, 0, 0);
        }
    };

    private ExecutorService executor;
    private OrderTable orders;
    private OwnerOrderService service;
    private UUID owner;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(4);
        orders = OrderTable.create();
        owner = orders.owner().getUniqueId();
        var history = new SqLiteHistoryData(SqLiteTestDatabase.mapper(), executor);
        var outcomes = new OrderOutcomes(
                orders.orderData(),
                new HistoryRecorder(history, Clock.systemDefaultZone()),
                new ItemReturnService(plugin, orders.orderData()),
                new EventRecorder(),
                ISimpleCompany::forId,
                Logger.getAnonymousLogger(),
                executor);
        service = new OwnerOrderService(
                orders.orderData(),
                outcomes,
                id -> CompletableFuture.completedFuture(Optional.empty()),
                order -> CompletableFuture.completedFuture(false),
                () -> settings,
                new Wallet(),
                amount -> collected.accumulateAndGet(amount, Double::sum),
                changed::add,
                SqLiteTestDatabase::player,
                Runnable::run,
                executor,
                Logger.getAnonymousLogger());
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
        MockBukkit.unmock();
    }

    private double wallet() {
        return wallets.getOrDefault(owner, 0.0);
    }

    private static <T> T done(ActionResult<T> result) {
        if (result instanceof ActionResult.Done<T>(var value)) return value;
        return fail("refused with " + refusalKey(result));
    }

    private static String refusalKey(ActionResult<?> result) {
        return result instanceof ActionResult.Refused<?> refused
                ? refused.failure().localeKey()
                : "done";
    }

    private static void assertRefused(String key, ActionResult<?> result) {
        assertEquals(key, refusalKey(result));
    }

    private boolean exists(SimpleOrder order) {
        return orders.orderData().retrieveOrderById(order.id()).join().isPresent();
    }

    @Test
    void cancellingRefundsThePriceAndCollectsTheFee() {
        var order = orders.place(FEE);

        var cancelled = done(service.cancel(owner, order.id()).join());

        assertEquals(order.id(), cancelled.id());
        assertEquals(PRICE, wallet());
        assertEquals(FEE, collected.get());
        assertFalse(exists(order));
        assertEquals(List.of(owner), changed);
    }

    @Test
    void onlyTheOwnerCancelsAnUnclaimedOrder() {
        var order = orders.place(FEE);

        assertRefused(
                "error.notYourOrder",
                service.cancel(UUID.randomUUID(), order.id()).join());
        assertRefused(
                "error.unknownOrder", service.cancel(owner, order.id() + 100).join());
        orders.state(order, OrderState.CLAIMED, 3);
        assertRefused(
                "error.orderAlreadyClaimed", service.cancel(owner, order.id()).join());

        assertTrue(exists(order));
        assertEquals(0.0, wallet());
        assertEquals(0.0, collected.get());
    }

    @Test
    void cancellableReportsTheOrderWithoutCancelling() {
        var order = orders.place(FEE);

        var cancellable = done(service.cancellable(owner, order.id()).join());

        assertEquals(order.id(), cancellable.order().id());
        assertTrue(cancellable.activeSeries().isEmpty());
        assertTrue(exists(order));
    }

    @Test
    void extendingCountsTheExtension() {
        var order = orders.place(FEE);
        var max = settings.maxExtensions();

        var extended = done(service.extend(owner, order.id()).join());

        assertEquals(max - 1, extended.left());
        assertEquals(Duration.ofHours(settings.maxUnclaimedHours()), extended.refundIn());
        assertEquals(max - 1, orders.reload(order).extensionsLeft(max));
    }

    @Test
    void extensionsRunOut() {
        var order = orders.place(FEE);
        for (var i = 0; i < settings.maxExtensions(); i++) {
            done(service.extend(owner, order.id()).join());
        }

        assertRefused("order.extend.noneLeft", service.extend(owner, order.id()).join());
        assertRefused(
                "error.unknownOrder",
                service.extend(UUID.randomUUID(), order.id()).join());
        orders.state(order, OrderState.CLAIMED, 3);
        assertRefused(
                "error.orderAlreadyClaimed", service.extend(owner, order.id()).join());
    }

    @Test
    void raisingChargesTheFeeOnlyOnTheIncrease() {
        var order = orders.place(FEE);
        wallets.put(owner, 200.0);

        var quote = done(service.raiseQuote(owner, order.id(), 100).join());
        assertEquals(10.0, quote.fee());
        assertEquals(200.0, wallet());

        var raised = done(service.raise(owner, order.id(), 100).join());

        assertEquals(PRICE + 100, raised.newPrice());
        assertEquals(90.0, wallet());
        var full = orders.full(order);
        assertEquals(PRICE + 100, full.price());
        assertEquals(FEE + 10, full.fee());
    }

    @Test
    void aRaiseTheOwnerCanNotAffordChangesNothing() {
        var order = orders.place(FEE);
        wallets.put(owner, 50.0);

        assertRefused(
                "error.insufficientCurrency",
                service.raise(owner, order.id(), 100).join());

        assertEquals(50.0, wallet());
        assertEquals(PRICE, orders.full(order).price());
    }

    @Test
    void raisesNeedAPositiveIncreaseOnAnOwnUnclaimedOrder() {
        var order = orders.place(FEE);
        wallets.put(owner, 500.0);

        assertRefused(
                "error.invalidNumber", service.raise(owner, order.id(), 0.001).join());
        assertRefused(
                "error.unknownOrder",
                service.raise(UUID.randomUUID(), order.id(), 10).join());
        orders.state(order, OrderState.CLAIMED, 3);
        assertRefused(
                "error.orderAlreadyClaimed",
                service.raise(owner, order.id(), 10).join());

        assertEquals(500.0, wallet());
    }

    private final class Wallet implements OrderCreationService.Wallet {
        @Override
        public synchronized boolean withdraw(OfflinePlayer player, double amount) {
            var current = wallets.getOrDefault(player.getUniqueId(), 0.0);
            if (current < amount) return false;
            wallets.put(player.getUniqueId(), current - amount);
            return true;
        }

        @Override
        public synchronized void deposit(OfflinePlayer player, double amount) {
            wallets.merge(player.getUniqueId(), amount, Double::sum);
        }

        @Override
        public String format(double amount) {
            return String.valueOf(amount);
        }

        @Override
        public String currencyName() {
            return "coins";
        }
    }
}
