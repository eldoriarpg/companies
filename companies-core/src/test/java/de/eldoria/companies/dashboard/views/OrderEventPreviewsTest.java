/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.views;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.event.EventKind;
import de.eldoria.companies.components.event.EventState;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.dashboard.DashboardWorld;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.orders.SystemOwner;
import de.eldoria.companies.util.Times;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.action.ActionRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dashboard's order book with the server events running now: the order search marks the orders an event boosts
 * and both the search and an open order's details show what the order pays with the events; a claimed chunk of an
 * event offer carries its offer in the company's orders.
 */
class OrderEventPreviewsTest {
    @TempDir
    Path directory;

    private DashboardWorld world;
    private UUID worker;
    private SimpleOrder order;

    @BeforeEach
    void setUp() throws Exception {
        world = DashboardWorld.create(directory);
        worker = world.player("Wren");
        world.company("Miners", Map.of(worker, List.of(CompanyPermission.OWNER)));
        order = world.order(world.player("Ottilie"), "Castle walls");
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    @Test
    void withoutEventsNoOrderIsMarked() {
        var found = search().getFirst();

        assertFalse(found.hasNonNull("event"));
        assertFalse(found.hasNonNull("payout"));
    }

    @Test
    void aRunningMultiplierMarksTheOrderAndRaisesItsPayout() {
        doubleWeek();

        var found = search().getFirst();
        var details = perform(Actions.ORDERS_DETAILS, "{\"order\":" + order.id() + "}");

        assertEquals(2.0, found.path("event").path("multiplier").asDouble());
        assertEquals(0.0, found.path("event").path("taxPoints").asDouble());
        assertTrue(found.path("event").hasNonNull("ends"));
        assertEquals(300.0, found.path("payout").path("net").asDouble(), 0.001);
        assertTrue(found.path("payout").hasNonNull("until"));
        assertEquals(
                found.path("payout").path("net").asDouble(),
                details.path("payout").path("net").asDouble());
        assertFalse(details.hasNonNull("event"));
    }

    @Test
    void anEventForOtherLevelsMarksNothingForTheCompany() {
        event(EventKind.REWARD_MULTIPLIER, 2, List.of(99));

        var found = search().getFirst();

        assertFalse(found.hasNonNull("event"));
        assertFalse(found.hasNonNull("payout"));
    }

    @Test
    void aPlayerWorkingAlonePaysTheSoloTaxLessTheReduction() {
        world.orderSettings.solo().taxPercent(10);
        event(EventKind.TAX_REDUCTION, 4, List.of());
        var loner = world.player("Lone");

        var found = search(loner).getFirst();

        assertEquals(4.0, found.path("event").path("taxPoints").asDouble());
        assertEquals(150 * 0.94, found.path("payout").path("net").asDouble(), 0.001);
    }

    @Test
    void theOwnerOfAnOrderSeesNoPayoutForIt() {
        doubleWeek();
        var owner = world.views.order(order.id()).orElseThrow().owner();

        var details = perform(owner, Actions.ORDERS_DETAILS, "{\"order\":" + order.id() + "}");

        assertFalse(details.hasNonNull("payout"));
    }

    @Test
    void aClaimedChunkCarriesItsOffer() {
        var goal = world.goal("Bridge logs", 0, true);
        var chunk = world.order(SystemOwner.ID, "Bridge logs");
        world.offers.addChunk(goal, chunk.id(), 10, 20, null, Times.now());

        var view = world.views.claimed(world.views.order(chunk.id()).orElseThrow());
        var plain = world.views.claimed(world.views.order(order.id()).orElseThrow());

        assertNotNull(view.offer());
        assertEquals(goal, view.offer().id());
        assertEquals("active", view.offer().state());
        assertEquals(null, plain.offer());
    }

    private void doubleWeek() {
        event(EventKind.REWARD_MULTIPLIER, 2, List.of());
    }

    private void event(EventKind kind, double value, List<Integer> levels) {
        var now = Times.now();
        var id = world.serverEvents
                .create(ServerEvent.draft(
                        "Week",
                        kind,
                        value,
                        List.of(),
                        levels,
                        now.minusMinutes(1),
                        now.plusDays(2),
                        MoneySource.SourceType.MINTED,
                        null,
                        null,
                        null,
                        "Ada",
                        now))
                .orElseThrow();
        world.serverEvents.state(id, EventState.SCHEDULED, EventState.ACTIVE, null, now);
    }

    private List<JsonNode> search() {
        return search(worker);
    }

    private List<JsonNode> search(UUID viewer) {
        var orders = new ArrayList<JsonNode>();
        perform(viewer, Actions.ORDERS_SEARCH, "{\"page\":1,\"pageSize\":10}")
                .path("orders")
                .forEach(orders::add);
        return orders;
    }

    private JsonNode perform(String action, String args) {
        return perform(worker, action, args);
    }

    private JsonNode perform(UUID viewer, String action, String args) {
        var now = Times.now();
        var session =
                new StoredSession("session", viewer, "Viewer", UUID.randomUUID(), now, now, now, now, null, Set.of());
        var result = world.reads.answer(
                new ActionRequest(
                        "act",
                        Protocol.VERSION,
                        "session",
                        viewer,
                        action,
                        Protocol.mapper().readTree(args)),
                session,
                feature -> true);
        assertTrue(result.ok(), result.reason());
        return result.data();
    }
}
