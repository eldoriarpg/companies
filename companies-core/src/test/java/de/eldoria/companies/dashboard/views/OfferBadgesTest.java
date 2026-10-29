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
import de.eldoria.companies.orders.SystemOwner;
import de.eldoria.companies.util.Times;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chunks of event offers in the dashboard's order book: always carrying their Event badge, also in the details,
 * found alone or per offer, and sorted by the end of their offer as the in-game search does.
 */
class OfferBadgesTest {
    private static final UUID NODE = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @TempDir
    Path directory;

    private DashboardWorld world;
    private UUID worker;
    private int later;
    private int sooner;
    private SimpleOrder laterChunk;
    private SimpleOrder soonerChunk;
    private SimpleOrder player;

    @BeforeEach
    void setUp() throws Exception {
        world = DashboardWorld.create(directory);
        worker = world.player("Wren");
        world.company("Miners", Map.of(worker, List.of(CompanyPermission.OWNER)));
        player = world.order(world.player("Ottilie"), "Castle walls");
        later = world.goal("Bridge logs", 2, true);
        sooner = world.goal("Harbour logs", 0, true);
        laterChunk = world.order(SystemOwner.ID, "Bridge logs");
        soonerChunk = world.order(SystemOwner.ID, "Harbour logs");
        world.offers.addChunk(later, laterChunk.id(), 10, 20, null, Times.now());
        world.offers.addChunk(sooner, soonerChunk.id(), 10, 20, null, Times.now());
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    @Test
    void chunksCarryTheirOfferEvenWhileServerOrdersAreNotMarked() {
        var orders = search("{\"page\":1,\"pageSize\":10}");

        var chunk = orders.stream()
                .filter(order -> order.path("id").asInt() == laterChunk.id())
                .findFirst()
                .orElseThrow();
        assertEquals(later, chunk.path("offer").path("id").asInt());
        assertEquals("Bridge logs", chunk.path("offer").path("name").asString());
        assertEquals("active", chunk.path("offer").path("state").asString());
        assertFalse(chunk.path("server").asBoolean());
        var own = orders.stream()
                .filter(order -> order.path("id").asInt() == player.id())
                .findFirst()
                .orElseThrow();
        assertFalse(own.hasNonNull("offer"));
    }

    @Test
    void chunksAreFoundAloneOrPerOffer() {
        assertEquals(
                List.of(laterChunk.id(), soonerChunk.id()),
                ids(search("{\"page\":1,\"pageSize\":10,\"offers\":\"only\"}")).stream()
                        .sorted()
                        .toList());
        assertEquals(List.of(soonerChunk.id()), ids(search("{\"page\":1,\"pageSize\":10,\"offer\":" + sooner + "}")));
    }

    @Test
    void theSoonestEndingOfferComesFirst() {
        var ascending = ids(search("{\"page\":1,\"pageSize\":10,\"sort\":\"offer_end\",\"asc\":true}"));
        var descending = ids(search("{\"page\":1,\"pageSize\":10,\"sort\":\"offer_end\",\"asc\":false}"));

        assertEquals(List.of(soonerChunk.id(), laterChunk.id(), player.id()), ascending);
        assertEquals(List.of(laterChunk.id(), soonerChunk.id(), player.id()), descending);
    }

    @Test
    void theDetailsOfAChunkCarryItsOffer() {
        var result = answer(Actions.ORDERS_DETAILS, "{\"order\":" + soonerChunk.id() + "}");

        assertTrue(result.ok());
        assertEquals(sooner, result.data().path("offer").path("id").asInt());
    }

    private List<JsonNode> search(String args) {
        var result = answer(Actions.ORDERS_SEARCH, args);
        var orders = new ArrayList<JsonNode>();
        result.data().path("orders").forEach(orders::add);
        return orders;
    }

    private ActionResult answer(String action, String args) {
        var request = new ActionRequest(
                "action-1",
                Protocol.VERSION,
                "session",
                worker,
                action,
                Protocol.mapper().readTree(args));
        var now = Times.now();
        var session = new StoredSession("session", worker, "Wren", NODE, now, now, now, now, null, Set.of());
        return world.reads.answer(request, session, feature -> true);
    }

    private static List<Integer> ids(List<JsonNode> orders) {
        return orders.stream().map(order -> order.path("id").asInt()).toList();
    }
}
