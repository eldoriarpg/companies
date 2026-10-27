/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.ordersettings.SoloSettings;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteHistoryData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.ReturnedItems;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.EventRecorder;
import de.eldoria.companies.events.order.PreSoloOrderAcceptEvent;
import de.eldoria.companies.events.order.SoloOrderAbortEvent;
import de.eldoria.companies.events.order.SoloOrderAcceptEvent;
import de.eldoria.companies.events.order.SoloOrderExpiredEvent;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import de.eldoria.companies.orders.SoloOrderService.Result;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.util.TestExecutors;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoloOrderServiceTest {
    private final SoloSettings settings = new SoloSettings();
    private final MovingClock clock = new MovingClock();
    private boolean inCompany;
    private Consumer<PreSoloOrderAcceptEvent> listener = event -> {};
    private SoloOrderService.Limit limit = SoloOrderService.Limit.NONE;

    @TempDir
    Path directory;

    private ServerMock server;
    private ExecutorService executor;
    private OrderTable orders;
    private PlayerMock player;
    private EventRecorder events;
    private SoloOrderService service;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(8);
        orders = OrderTable.create();
        player = server.addPlayer();
        player.addAttachment(plugin, Permission.Orders.SOLO, true);
        events = EventRecorder.listen(plugin, ItemsReturnedEvent.class);
        settings.enabled(true);
        settings.maxActive(1);
        settings.maxOrderSize(64);
        settings.abortCooldownMinutes(60);
        var outcomes = new OrderOutcomes(
                orders.orderData(),
                new HistoryRecorder(new SqLiteHistoryData(SqLiteTestDatabase.mapper(), executor), clock()),
                new ItemReturnService(plugin, orders.orderData()),
                events,
                ISimpleCompany::forId,
                Logger.getAnonymousLogger(),
                executor);
        service = new SoloOrderService(
                orders.orderData(),
                outcomes,
                () -> settings,
                ignored -> inCompany,
                server::getOfflinePlayer,
                event -> {
                    events.accept(event);
                    if (event instanceof PreSoloOrderAcceptEvent pre) listener.accept(pre);
                },
                clock(),
                executor,
                (worker, order) -> limit.refusal(worker, order));
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
        MockBukkit.unmock();
    }

    private Clock clock() {
        return clock;
    }

    private Result<FullOrder> accept(SimpleOrder order) {
        return service.accept(player, order.id()).join();
    }

    private static String refusal(Result<?> result) {
        return assertInstanceOf(Result.Refused.class, result).failure().localeKey();
    }

    private static void ageClaim(SimpleOrder order, int hours) {
        query("UPDATE order_states SET last_update = datetime(CURRENT_TIMESTAMP, '-' || ? || ' HOUR') WHERE id = ?")
                .single(call().bind(hours).bind(order.id()))
                .update();
    }

    @Test
    void acceptingClaimsTheOrderAloneAndFiresBothEvents() {
        var order = orders.place(0.0);

        var accepted = assertInstanceOf(Result.Done.class, accept(order));

        var claimed = orders.reload(order);
        assertEquals(OrderState.CLAIMED, claimed.state());
        assertEquals(Optional.of(player.getUniqueId()), claimed.soloWorker());
        assertEquals(order.id(), ((FullOrder) accepted.value()).id());
        assertEquals(1, events.of(PreSoloOrderAcceptEvent.class).size());
        assertEquals(player, events.single(SoloOrderAcceptEvent.class).player());
    }

    @Test
    void nothingIsAcceptedWhileSoloOrdersAreOff() {
        settings.enabled(false);

        assertEquals("solo.error.disabled", refusal(accept(orders.place(0.0))));
        assertTrue(events.of(PreSoloOrderAcceptEvent.class).isEmpty());
    }

    @Test
    void playersWithoutThePermissionAreRefused() {
        var stranger = server.addPlayer();

        assertEquals(
                "solo.error.permission",
                refusal(service.accept(stranger, orders.place(0.0).id()).join()));
    }

    @Test
    void companyMembersAreRefusedUnlessAllowed() {
        inCompany = true;
        var order = orders.place(0.0);

        assertEquals("solo.error.companyMember", refusal(accept(order)));

        settings.forCompanyMembers(true);
        assertInstanceOf(Result.Done.class, accept(order));
    }

    @Test
    void playersAtTheLimitAreRefused() {
        assertInstanceOf(Result.Done.class, accept(orders.place(0.0)));

        assertEquals("solo.error.limit", refusal(accept(orders.place(0.0))));

        settings.maxActive(2);
        assertInstanceOf(Result.Done.class, accept(orders.place(0.0)));
    }

    @Test
    void tooLargeOrdersAreRefused() {
        settings.maxOrderSize(14);

        assertEquals("solo.error.tooLarge", refusal(accept(orders.place(0.0))));

        settings.maxOrderSize(0);
        assertInstanceOf(Result.Done.class, accept(orders.place(0.0)));
    }

    @Test
    void ownOrdersAreRefused() {
        var own = new OrderTable(orders.orderData(), player).place(0.0);

        assertEquals("solo.error.ownOrder", refusal(accept(own)));
    }

    @Test
    void aLimitRefusesTheClaimAndLeavesTheOrderUnclaimed() {
        limit = (worker, order) -> Optional.of(CommandFailure.actionBar("solo.error.guessedLimit"));
        var order = orders.place(0.0);

        assertEquals("solo.error.guessedLimit", refusal(accept(order)));
        assertEquals(OrderState.UNCLAIMED, orders.reload(order).state());
        assertTrue(events.of(SoloOrderAcceptEvent.class).isEmpty());
    }

    @Test
    void claimedOrdersAreNotAcceptedAgain() {
        var order = orders.place(0.0);
        orders.state(order, OrderState.CLAIMED, 5);

        assertEquals("error.orderNotClaimable", refusal(accept(order)));
    }

    @Test
    void cancellingThePreEventLeavesTheOrderUnclaimed() {
        listener = event -> event.cancel(Component.text("Not today"));
        var order = orders.place(0.0);

        assertEquals("error.cancelledByIntegration", refusal(accept(order)));
        assertEquals(OrderState.UNCLAIMED, orders.reload(order).state());
        assertTrue(events.of(SoloOrderAcceptEvent.class).isEmpty());
    }

    @Test
    void abortingReturnsTheItemsAndStartsTheCooldown() {
        var order = orders.place(0.0);
        accept(order);
        orders.deliver(order, 0, Material.STONE, player.getUniqueId(), 3);

        var aborted = service.abort(player, order.id()).join();

        var returned =
                (ReturnedItems) assertInstanceOf(Result.Done.class, aborted).value();
        assertEquals(3, returned.byWorker().get(player.getUniqueId()).get(0).amount());
        assertEquals(OrderState.UNCLAIMED, orders.reload(order).state());
        assertEquals(player, events.single(SoloOrderAbortEvent.class).player());
        assertEquals(
                Optional.of(Duration.ofMinutes(60)),
                service.cooldownLeft(player.getUniqueId()).join());
        assertEquals("solo.error.cooldown", refusal(accept(orders.place(0.0))));
    }

    @Test
    void theCooldownEnds() {
        var order = orders.place(0.0);
        accept(order);
        service.abort(player, order.id()).join();

        clock.advance(Duration.ofMinutes(61));

        assertEquals(
                Optional.empty(), service.cooldownLeft(player.getUniqueId()).join());
        assertInstanceOf(Result.Done.class, accept(orders.place(0.0)));
    }

    @Test
    void withoutCooldownAnAbortDoesNotBlock() {
        settings.abortCooldownMinutes(0);
        var order = orders.place(0.0);
        accept(order);
        service.abort(player, order.id()).join();

        assertInstanceOf(Result.Done.class, accept(orders.place(0.0)));
    }

    @Test
    void ordersOfOthersAreNotAborted() {
        var order = orders.place(0.0);
        orders.orderData()
                .submitSoloClaim(server.addPlayer().getUniqueId(), order, 1)
                .join();
        var companyOrder = orders.place(0.0);
        orders.state(companyOrder, OrderState.CLAIMED, 5);

        assertEquals(
                "solo.error.notYours", refusal(service.abort(player, order.id()).join()));
        assertEquals(
                "solo.error.notYours",
                refusal(service.abort(player, companyOrder.id()).join()));
        assertEquals("error.unknownOrder", refusal(service.abort(player, 999).join()));
        assertEquals(OrderState.CLAIMED, orders.reload(order).state());
    }

    @Test
    void workerOrdersAreListedAndLoaded() {
        var order = orders.place(0.0);
        accept(order);

        assertEquals(
                List.of(order.id()),
                service.workerOrders(player.getUniqueId()).join().stream()
                        .map(FullOrder::id)
                        .toList());
        assertInstanceOf(
                Result.Done.class, service.workerOrder(player, order.id()).join());
        assertEquals(
                "solo.error.notYours",
                refusal(service.workerOrder(server.addPlayer(), order.id()).join()));
    }

    @Test
    void expiredClaimsAreReleasedWithTheItemsAndTheCooldown() {
        settings.deliveryHours(24);
        var expired = orders.place(0.0);
        accept(expired);
        orders.deliver(expired, 0, Material.STONE, player.getUniqueId(), 2);
        ageClaim(expired, 25);
        var other = server.addPlayer();
        var fresh = orders.place(0.0);
        orders.orderData().submitSoloClaim(other.getUniqueId(), fresh, 1).join();

        var released = service.releaseExpired().join();

        assertEquals(
                List.of(expired.id()), released.stream().map(SimpleOrder::id).toList());
        assertEquals(OrderState.UNCLAIMED, orders.reload(expired).state());
        assertEquals(OrderState.CLAIMED, orders.reload(fresh).state());
        assertEquals(
                player.getUniqueId(),
                events.single(SoloOrderExpiredEvent.class).player().getUniqueId());
        assertEquals(
                ItemsReturnedEvent.Reason.ORDER_EXPIRED,
                events.single(ItemsReturnedEvent.class).reason());
        assertTrue(service.cooldownLeft(player.getUniqueId()).join().isPresent());
        assertTrue(service.releaseExpired().join().isEmpty());
    }

    private static final class MovingClock extends Clock {
        private Instant now = Instant.parse("2026-06-01T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
