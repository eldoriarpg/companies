/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.views;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.dashboard.DashboardWorld;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.util.Times;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.Features;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import de.eldoria.companies.web.api.snapshot.OrderState;
import de.eldoria.companies.web.api.snapshot.Snapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dashboard shows what the game shows: the owner never sees the company or worker of an order, the company never
 * sees the owner, and inside the company the members are named.
 */
class AnonymityTest {
    private static final UUID NODE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @TempDir
    Path directory;

    private DashboardWorld world;
    private UUID owner;
    private UUID kestrel;
    private UUID wren;
    private UUID outsider;
    private int miners;
    private SimpleOrder claimed;
    private SimpleOrder open;

    @BeforeEach
    void setUp() throws Exception {
        world = DashboardWorld.create(directory);
        owner = world.player("Ottilie");
        kestrel = world.player("Kestrel_");
        wren = world.player("Wren");
        outsider = world.player("Xavi");
        miners = world.company("Miners", Map.of(kestrel, List.of(CompanyPermission.OWNER), wren, List.of()));
        world.company("Farmers", Map.of(outsider, List.of(CompanyPermission.OWNER)));
        claimed = world.order(owner, "Market stalls");
        open = world.order(owner, "Castle walls");
        world.claim(claimed, miners);
        world.deliver(claimed, wren, 3);
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    private Snapshot snapshot(UUID player) {
        return world.views.snapshot("request", "session-" + player, player, NODE, 0);
    }

    private String json(Object value) {
        return Protocol.mapper().writeValueAsString(value);
    }

    private ActionResult read(UUID player, String action, String args) {
        var request = new ActionRequest(
                "action-1",
                Protocol.VERSION,
                "session",
                player,
                action,
                Protocol.mapper().readTree(args));
        return world.reads.answer(request, session(player), feature -> true);
    }

    private static StoredSession session(UUID player) {
        var now = Times.now();
        return new StoredSession("session", player, "name", NODE, now, now, now, now, null, Set.of());
    }

    @Test
    void theOwnerSeesTheOrdersWithoutCompanyOrWorker() {
        var snapshot = snapshot(owner);

        assertNull(snapshot.company());
        assertEquals(2, snapshot.orders().size());
        var order = snapshot.orders().stream()
                .filter(view -> view.id() == claimed.id())
                .findFirst()
                .orElseThrow();
        assertEquals(OrderState.CLAIMED, order.state());
        assertEquals(3, order.lines().getFirst().delivered());
        assertNotNull(order.deadline());
        var text = json(snapshot.orders()) + json(snapshot.player());
        assertFalse(text.contains("Miners"));
        assertFalse(text.contains("Kestrel_"));
        assertFalse(text.contains("Wren"));
        assertFalse(text.contains(wren.toString()));
        assertFalse(json(order).contains("company"));
    }

    @Test
    void theCompanySeesItsOrderWithoutTheOwnerAndNamesItsMembers() {
        var snapshot = snapshot(wren);

        var company = snapshot.company();
        assertNotNull(company);
        assertEquals("Miners", company.name());
        assertEquals(
                List.of(claimed.id()),
                company.orders().stream().map(view -> view.id()).toList());
        assertEquals(3, company.orders().getFirst().lines().getFirst().delivered());
        assertEquals(
                List.of("Kestrel_", "Wren"),
                company.members().stream().map(member -> member.name()).toList());
        assertTrue(snapshot.orders().isEmpty());
        var text = json(snapshot);
        assertFalse(text.contains("Ottilie"));
        assertFalse(text.contains(owner.toString()));
    }

    @Test
    void theTreasuryIsShownToMembersWithTheTreasuryPermissionOnly() {
        assertNull(snapshot(wren).company().treasury());
        assertNotNull(snapshot(kestrel).company().treasury());
    }

    @Test
    void orderDetailsDependOnWhoAsks() {
        var forOwner = read(owner, Actions.ORDERS_DETAILS, "{\"order\":" + claimed.id() + "}");
        var forMember = read(wren, Actions.ORDERS_DETAILS, "{\"order\":" + claimed.id() + "}");
        var forOutsider = read(outsider, Actions.ORDERS_DETAILS, "{\"order\":" + claimed.id() + "}");
        var openForOutsider = read(outsider, Actions.ORDERS_DETAILS, "{\"order\":" + open.id() + "}");

        assertTrue(forOwner.ok());
        assertEquals("CLAIMED", forOwner.data().path("state").asString());
        assertFalse(forOwner.data().has("claimed"));
        assertTrue(forMember.data().has("claimed"));
        assertFalse(json(forOwner.data()).contains("Miners"));
        assertFalse(json(forOwner.data()).contains(wren.toString()));
        assertTrue(forMember.ok());
        assertFalse(json(forMember.data()).contains(owner.toString()));
        assertFalse(forOutsider.ok());
        assertEquals("dashboard.orderUnknown", forOutsider.reason());
        assertTrue(openForOutsider.ok());
        assertFalse(json(openForOutsider.data()).contains(owner.toString()));
        assertEquals("Castle walls", openForOutsider.data().path("name").asString());
    }

    @Test
    void theSearchShowsOpenOrdersWithoutTheirOwner() {
        var result = read(outsider, Actions.ORDERS_SEARCH, "{\"page\":1}");

        assertTrue(result.ok());
        JsonNode orders = result.data().path("orders");
        assertEquals(1, orders.size());
        assertEquals(1, result.data().path("pages").asInt());
        assertEquals(1, result.data().path("total").asInt());
        assertEquals("UNCLAIMED", orders.get(0).path("state").asString());
        assertTrue(orders.get(0).has("expires"));
        assertFalse(orders.get(0).has("deadline"));
        assertEquals(open.id(), orders.get(0).path("id").asInt());
        assertFalse(json(result.data()).contains(owner.toString()));
        assertFalse(json(result.data()).contains("Ottilie"));
    }

    @Test
    void theOwnOrderListPagesThroughTheOwnersOrders() {
        var first = read(owner, Actions.ORDERS_OWN_LIST, "{\"page\":1,\"pageSize\":1}");
        var second = read(owner, Actions.ORDERS_OWN_LIST, "{\"page\":2,\"pageSize\":1}");

        assertEquals(2, first.data().path("pages").asInt());
        assertEquals(2, first.data().path("total").asInt());
        assertEquals(1, second.data().path("orders").size());
        assertFalse(json(first.data()).contains("Miners"));
        assertFalse(json(second.data()).contains(wren.toString()));
    }

    @Test
    void writingActionsAndSwitchedOffFeaturesAreRefused() {
        var write = read(kestrel, Actions.ORDERS_ACCEPT, "{\"order\":" + open.id() + "}");
        var unknown = read(kestrel, "orders.teleport", "{}");
        var request = new ActionRequest(
                "action-2",
                Protocol.VERSION,
                "session",
                kestrel,
                Actions.INBOX_PAGE,
                Protocol.mapper().readTree("{}"));
        var disabled = world.reads.answer(request, session(kestrel), feature -> !feature.equals(Features.INBOX));

        assertEquals("dashboard.readOnly", write.reason());
        assertEquals("text of dashboard.readOnly", write.message());
        assertEquals("dashboard.unsupported", unknown.reason());
        assertEquals("dashboard.featureDisabled", disabled.reason());
        assertEquals("action-1", write.id());
    }

    @Test
    void theApplicationsAndTreasuryLogNeedTheirPermissions() {
        assertEquals(
                "error.permission.invite",
                read(wren, Actions.APPLICATIONS_LIST, "{}").reason());
        assertEquals(
                "error.permission.treasury",
                read(wren, Actions.TREASURY_LOG, "{}").reason());
        assertTrue(read(kestrel, Actions.APPLICATIONS_LIST, "{}").ok());
        assertTrue(read(kestrel, Actions.TREASURY_LOG, "{}").ok());
        assertEquals("error.noMember", read(owner, Actions.TREASURY_LOG, "{}").reason());
    }

    @Test
    void theRankingsAnswerPagesAndTheOwnPlace() {
        var page = read(kestrel, Actions.RANKINGS_PAGE, "{\"ranking\":\"orders\",\"range\":\"all\",\"page\":1}");
        var own = read(kestrel, Actions.RANKINGS_OWN, "{\"ranking\":\"orders\",\"range\":\"all\"}");
        var invalid = read(kestrel, Actions.RANKINGS_PAGE, "{\"ranking\":\"wealth\"}");

        assertTrue(page.ok());
        assertEquals("orders", page.data().path("ranking").asString());
        assertTrue(own.ok());
        assertEquals(miners, own.data().path("company").asInt());
        assertEquals("dashboard.invalid", invalid.reason());
    }
}
