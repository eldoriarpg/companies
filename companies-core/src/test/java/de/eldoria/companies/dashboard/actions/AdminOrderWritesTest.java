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
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.util.Permission;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dashboard's admin tools for orders: details and searches of any order, and the admin's changes to it, each
 * allowed with {@code companies.admin.orders} only and written to the admin log.
 */
class AdminOrderWritesTest {
    private static final Set<String> ADMIN = Set.of(Permission.Admin.ORDERS);

    @TempDir
    Path directory;

    private DashboardWorld world;
    private UUID admin;
    private UUID owner;
    private UUID worker;
    private int company;

    @BeforeEach
    void setUp() throws Exception {
        world = DashboardWorld.create(directory);
        admin = world.player("Ada");
        owner = world.player("Orla");
        worker = world.player("Moss");
        company = world.company("Miners", Map.of(worker, List.of(CompanyPermission.MANAGE_ORDERS)));
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    private ActionResult perform(String action, String args, Set<String> permissions) {
        var session = new StoredSession(
                "session",
                admin,
                "Ada",
                UUID.randomUUID(),
                Times.now(),
                Times.now(),
                Times.now(),
                Times.now(),
                null,
                permissions);
        return world.writes.perform(
                new ActionRequest(
                        "act",
                        Protocol.VERSION,
                        session.id(),
                        admin,
                        action,
                        Protocol.mapper().readTree(args)),
                session);
    }

    private SimpleOrder claimed() {
        var order = world.order(owner, "Walls");
        world.claim(order, company);
        world.deliver(order, worker, 4);
        return order;
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
    void anAdminWithoutThePermissionSeesNothing() {
        var order = claimed();

        var refused = perform(Actions.ADMIN_ORDERS_INFO, "{\"order\":" + order.id() + "}", Set.of());

        assertEquals("companyadmin.error.permission", refused.reason());
        assertTrue(world.adminActions.isEmpty());
    }

    @Test
    void theDetailsNameBothSidesTheWorkersAndWhatAChangeWouldMove() {
        var order = claimed();

        var info = perform(Actions.ADMIN_ORDERS_INFO, "{\"order\":" + order.id() + "}", ADMIN);

        assertTrue(info.ok(), info.reason());
        var data = info.data();
        assertEquals("Orla", data.path("owner").path("name").asString());
        assertEquals("Miners", data.path("company").asString());
        assertEquals("CLAIMED", data.path("state").asString());
        assertEquals(4, data.path("lines").get(0).path("workers").path("Moss").asInt());
        assertFalse(data.path("removal").asBoolean());
        assertTrue(data.path("refundOnDeliver").asDouble() > 0);
        assertTrue(world.adminActions.getLast().contains("Ada (dashboard) order info id=" + order.id()));
    }

    @Test
    void ordersAreFoundByOwnerAndByCompany() {
        var order = claimed();

        var byOwner = perform(Actions.ADMIN_ORDERS_SEARCH, "{\"player\":\"orla\"}", ADMIN);
        var byCompany = perform(Actions.ADMIN_ORDERS_SEARCH, "{\"company\":\"Miners\"}", ADMIN);

        assertTrue(byOwner.ok(), byOwner.reason());
        assertEquals(order.id(), byOwner.data().path("orders").get(0).path("id").asInt());
        assertEquals(
                "Miners", byOwner.data().path("orders").get(0).path("claimant").asString());
        assertEquals(
                order.id(), byCompany.data().path("orders").get(0).path("id").asInt());
        assertEquals(
                "error.unknownPlayer",
                perform(Actions.ADMIN_ORDERS_SEARCH, "{\"player\":\"nobody\"}", ADMIN)
                        .reason());
        assertEquals(
                "dashboard.invalid",
                perform(Actions.ADMIN_ORDERS_SEARCH, "{}", ADMIN).reason());
    }

    @Test
    void aClaimedOrderIsTakenFromItsCompanyAndCannotBeTakenTwice() {
        var order = claimed();

        var unclaimed = perform(Actions.ADMIN_ORDERS_UNCLAIM, "{\"order\":" + order.id() + "}", ADMIN);

        assertTrue(unclaimed.ok(), unclaimed.reason());
        assertEquals(OrderState.UNCLAIMED, state(order));
        assertEquals(
                "companyadmin.order.error.notClaimed",
                perform(Actions.ADMIN_ORDERS_UNCLAIM, "{\"order\":" + order.id() + "}", ADMIN)
                        .reason());
    }

    @Test
    void aCancelWithRefundGivesTheOwnerPriceAndFeeBack() {
        var order = claimed();

        var cancelled = perform(Actions.ADMIN_ORDERS_CANCEL, "{\"order\":" + order.id() + ",\"refund\":true}", ADMIN);

        assertTrue(cancelled.ok(), cancelled.reason());
        assertEquals(cancelled.data().path("refunded").asDouble(), world.wallets.getOrDefault(owner, 0.0), 0.001);
        assertEquals(
                "dashboard.invalid",
                perform(Actions.ADMIN_ORDERS_CANCEL, "{\"order\":1}", ADMIN).reason());
    }

    @Test
    void anOrderIsExtendedByWholeHoursWithinAYear() {
        var order = claimed();

        assertEquals(
                "companyadmin.order.error.invalidHours",
                perform(Actions.ADMIN_ORDERS_EXTEND, "{\"order\":" + order.id() + ",\"hours\":0}", ADMIN)
                        .reason());
        var extended = perform(Actions.ADMIN_ORDERS_EXTEND, "{\"order\":" + order.id() + ",\"hours\":12}", ADMIN);

        assertTrue(extended.ok(), extended.reason());
        assertEquals(12, extended.data().path("hours").asInt());
    }

    @Test
    void aClaimedOrderIsMarkedDelivered() {
        var order = claimed();

        var delivered = perform(Actions.ADMIN_ORDERS_DELIVER, "{\"order\":" + order.id() + "}", ADMIN);

        assertTrue(delivered.ok(), delivered.reason());
        assertEquals(OrderState.DELIVERED, state(order));
        assertEquals(
                "error.unknownOrder",
                perform(Actions.ADMIN_ORDERS_DELIVER, "{\"order\":0}", ADMIN).reason());
    }
}
