/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.dashboard.DashboardWorld;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.inbox.InboxStatus;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationType;
import de.eldoria.companies.util.Times;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dashboard's actions that change something, against the real services and a SQLite database.
 */
class DashboardWritesTest {
    @TempDir
    Path directory;

    private DashboardWorld world;
    private UUID owner;
    private UUID manager;
    private UUID worker;
    private int company;

    @BeforeEach
    void setUp() throws Exception {
        world = DashboardWorld.create(directory);
        owner = world.player("Orla");
        manager = world.player("Wren");
        worker = world.player("Moss");
        company = world.company("Miners", Map.of(manager, List.of(CompanyPermission.MANAGE_ORDERS), worker, List.of()));
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    private ActionResult perform(UUID player, String action, String args) {
        var session = new StoredSession(
                "session",
                player,
                "player",
                UUID.randomUUID(),
                Times.now(),
                Times.now(),
                Times.now(),
                Times.now(),
                null,
                Set.of());
        return world.writes.perform(
                new ActionRequest("act", Protocol.VERSION, session.id(), player, action, json(args)), session);
    }

    private static tools.jackson.databind.JsonNode json(String args) {
        return Protocol.mapper().readTree(args);
    }

    private double price(SimpleOrder order) {
        world.orders.forgetFullOrder(order.id());
        var simple =
                world.orders.retrieveOrderById(order.id()).asFuture().join().orElseThrow();
        return world.orders.retrieveFullOrder(simple).asFuture().join().price();
    }

    private OrderState state(SimpleOrder order) {
        world.orders.forgetFullOrder(order.id());
        return world.orders
                .retrieveOrderById(order.id())
                .asFuture()
                .join()
                .orElseThrow()
                .state();
    }

    @Test
    void aMemberWhoMayManageOrdersAcceptsForTheCompany() {
        var order = world.order(owner, "Stone");

        var result = perform(manager, Actions.ORDERS_ACCEPT, "{\"order\":" + order.id() + "}");

        assertTrue(result.ok(), result.reason());
        assertEquals(order.id(), result.data().path("order").asInt());
        assertEquals(OrderState.CLAIMED, state(order));
    }

    @Test
    void aMemberWithoutThePermissionIsRefusedWithTheServicesReason() {
        var order = world.order(owner, "Stone");

        var result = perform(worker, Actions.ORDERS_ACCEPT, "{\"order\":" + order.id() + "}");

        assertFalse(result.ok());
        assertEquals("error.permission.acceptOrder", result.reason());
        assertEquals("text of error.permission.acceptOrder", result.message());
        assertNull(result.data());
        assertEquals(OrderState.UNCLAIMED, state(order));
    }

    @Test
    void aPlayerWithoutACompanyCannotAccept() {
        var order = world.order(owner, "Stone");

        var result = perform(world.player("Loner"), Actions.ORDERS_ACCEPT, "{\"order\":" + order.id() + "}");

        assertFalse(result.ok());
        assertEquals("error.noMember", result.reason());
    }

    @Test
    void aManagerAbortsAClaimedOrder() {
        var order = world.order(owner, "Stone");
        world.claim(order, company);

        var result = perform(manager, Actions.ORDERS_ABORT, "{\"order\":" + order.id() + "}");

        assertTrue(result.ok(), result.reason());
        assertEquals(OrderState.UNCLAIMED, state(order));
    }

    @Test
    void aQuotedCancelChangesNothingAndTheRealOneRefundsTheOwner() {
        var order = world.order(owner, "Stone");

        var quote = perform(owner, Actions.ORDERS_OWN_CANCEL, "{\"order\":" + order.id() + ",\"quote\":true}");
        assertTrue(quote.ok(), quote.reason());
        assertFalse(quote.data().path("repeating").asBoolean(true));
        assertEquals(OrderState.UNCLAIMED, state(order));

        var cancel = perform(owner, Actions.ORDERS_OWN_CANCEL, "{\"order\":" + order.id() + "}");
        assertTrue(cancel.ok(), cancel.reason());
        assertEquals(150.0, world.wallets.get(owner));
        assertTrue(world.orders.retrieveOrderById(order.id()).asFuture().join().isEmpty());
    }

    @Test
    void onlyTheOwnerCancelsAnOrder() {
        var order = world.order(owner, "Stone");

        var result = perform(worker, Actions.ORDERS_OWN_CANCEL, "{\"order\":" + order.id() + "}");

        assertFalse(result.ok());
        assertEquals("error.notYourOrder", result.reason());
    }

    @Test
    void anOwnerExtendsAnOpenOrder() {
        var order = world.order(owner, "Stone");

        var result = perform(owner, Actions.ORDERS_OWN_EXTEND, "{\"order\":" + order.id() + "}");

        assertTrue(result.ok(), result.reason());
        assertEquals(
                world.orderSettings.maxExtensions() - 1,
                result.data().path("left").asInt());
    }

    @Test
    void aQuotedRaiseChargesNothingAndTheRealOneChargesTheTotal() {
        var order = world.order(owner, "Stone");
        world.wallets.put(owner, 100.0);

        var quote = perform(
                owner, Actions.ORDERS_OWN_RAISE, "{\"order\":" + order.id() + ",\"increase\":20,\"quote\":true}");
        assertTrue(quote.ok(), quote.reason());
        var total = quote.data().path("total").asDouble();
        assertEquals(20.0 + quote.data().path("fee").asDouble(), total);
        assertEquals(100.0, world.wallets.get(owner));
        assertEquals(150.0, price(order));

        var raise = perform(owner, Actions.ORDERS_OWN_RAISE, "{\"order\":" + order.id() + ",\"increase\":20}");
        assertTrue(raise.ok(), raise.reason());
        assertEquals(100.0 - total, world.wallets.get(owner), 0.001);
        assertEquals(170.0, price(order), 0.001);
    }

    @Test
    void aRaiseNeedsAPositiveIncrease() {
        var order = world.order(owner, "Stone");

        var result = perform(owner, Actions.ORDERS_OWN_RAISE, "{\"order\":" + order.id() + ",\"increase\":-5}");

        assertFalse(result.ok());
        assertEquals("error.invalidNumber", result.reason());
    }

    @Test
    void theInboxIsReadClearedAndMuted() {
        world.inbox
                .submit(owner, NotificationType.PAYMENT, NotificationArgs.create(), Times.now(), InboxStatus.PENDING)
                .join();

        var read = perform(owner, Actions.INBOX_READ_ALL, "{}");
        assertTrue(read.ok(), read.reason());
        assertEquals(1, read.data().path("marked").asInt());

        var cleared = perform(owner, Actions.INBOX_CLEAR, "{}");
        assertTrue(cleared.ok(), cleared.reason());
        assertEquals(1, cleared.data().path("deleted").asInt());

        var muted = perform(owner, Actions.INBOX_MUTE, "{\"category\":\"orders\",\"mute\":true}");
        assertTrue(muted.ok(), muted.reason());
        assertTrue(muted.data().path("muted").asBoolean());
        var unmuted = perform(owner, Actions.INBOX_MUTE, "{\"category\":\"ORDERS\",\"mute\":false}");
        assertFalse(unmuted.data().path("muted").asBoolean(true));
    }

    @Test
    void aCategoryThatCannotBeMutedIsRefused() {
        var result = perform(owner, Actions.INBOX_MUTE, "{\"category\":\"company\"}");
        assertFalse(result.ok());
        assertEquals("dashboard.unknownCategory", result.reason());

        var unknown = perform(owner, Actions.INBOX_MUTE, "{\"category\":\"nothing\"}");
        assertEquals("dashboard.unknownCategory", unknown.reason());
    }

    @Test
    void aMemberWhoMayNotManageOrdersCannotAbort() {
        var order = world.order(owner, "Stone");
        world.claim(order, company);

        var result = perform(worker, Actions.ORDERS_ABORT, "{\"order\":" + order.id() + "}");

        assertFalse(result.ok());
        assertEquals("error.permission.cancelOrder", result.reason());
        assertEquals(OrderState.CLAIMED, state(order));
    }

    @Test
    void anOrderThatIsNoWholePositiveNumberIsUnknown() {
        for (var args : List.of("{}", "{\"order\":0}", "{\"order\":-3}", "{\"order\":2.9}", "{\"order\":\"7\"}")) {
            var result = perform(manager, Actions.ORDERS_ACCEPT, args);
            assertFalse(result.ok(), args);
            assertEquals("error.unknownOrder", result.reason(), args);
        }
    }

    @Test
    void aRaiseNeedsANumber() {
        var order = world.order(owner, "Stone");

        for (var increase : List.of("\"abc\"", "null", "0", "-5")) {
            var result = perform(
                    owner, Actions.ORDERS_OWN_RAISE, "{\"order\":" + order.id() + ",\"increase\":" + increase + "}");
            assertFalse(result.ok(), increase);
            assertEquals("error.invalidNumber", result.reason(), increase);
        }
    }

    @Test
    void extendingAndRaisingTellThePatchFeedReadingTheInboxToo() {
        var order = world.order(owner, "Stone");
        world.wallets.put(owner, 100.0);

        perform(owner, Actions.ORDERS_OWN_RAISE, "{\"order\":" + order.id() + ",\"increase\":5,\"quote\":true}");
        assertTrue(world.changedOrders.isEmpty());
        perform(owner, Actions.ORDERS_OWN_EXTEND, "{\"order\":" + order.id() + "}");
        perform(owner, Actions.ORDERS_OWN_RAISE, "{\"order\":" + order.id() + ",\"increase\":5}");
        assertEquals(List.of(order.id(), order.id()), world.changedOrders);

        perform(owner, Actions.INBOX_READ_ALL, "{}");
        perform(owner, Actions.INBOX_CLEAR, "{}");
        assertEquals(List.of(owner, owner), world.changedInboxes);
    }

    @Test
    void aMuteNeedsToSayWhetherToMute() {
        var result = perform(owner, Actions.INBOX_MUTE, "{\"category\":\"orders\"}");
        assertFalse(result.ok());
        assertEquals("dashboard.invalid", result.reason());
    }
}
