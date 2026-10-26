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

/**
 * Orders the server placed itself in the dashboard's order book: marked and filtered only while the server marks
 * them, and listed after player orders that sort the same.
 */
class ServerMarksTest {
    private static final UUID NODE = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @TempDir
    Path directory;

    private DashboardWorld world;
    private UUID worker;
    private SimpleOrder server;
    private SimpleOrder player;

    @BeforeEach
    void setUp() throws Exception {
        world = DashboardWorld.create(directory);
        worker = world.player("Wren");
        world.company("Miners", Map.of(worker, List.of(CompanyPermission.OWNER)));
        server = world.order(SystemOwner.ID, "Royal smithy");
        player = world.order(world.player("Ottilie"), "Castle walls");
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    @Test
    void withoutMarkingServerOrdersLookLikeAnyOrder() {
        var orders = search("{\"page\":1,\"pageSize\":10,\"serverOrders\":\"only\"}");

        assertEquals(2, orders.size());
        orders.forEach(order -> assertEquals(false, order.path("server").asBoolean()));
    }

    @Test
    void whileMarkingServerOrdersCarryTheirMarkAndCanBeFiltered() {
        world.systemOrders.markSystemOrders(true);

        var all = search("{\"page\":1,\"pageSize\":10,\"sort\":\"PRICE\"}");
        var only = search("{\"page\":1,\"pageSize\":10,\"serverOrders\":\"only\"}");
        var hidden = search("{\"page\":1,\"pageSize\":10,\"serverOrders\":\"hide\"}");

        assertEquals(List.of(player.id(), server.id()), ids(all));
        assertEquals(true, all.get(1).path("server").asBoolean());
        assertEquals(List.of(server.id()), ids(only));
        assertEquals(List.of(player.id()), ids(hidden));
    }

    private List<JsonNode> search(String args) {
        var request = new ActionRequest(
                "action-1",
                Protocol.VERSION,
                "session",
                worker,
                Actions.ORDERS_SEARCH,
                Protocol.mapper().readTree(args));
        var now = Times.now();
        var session = new StoredSession("session", worker, "Wren", NODE, now, now, now, now, null, Set.of());
        var result = world.reads.answer(request, session, feature -> true);
        var orders = new ArrayList<JsonNode>();
        result.data().path("orders").forEach(orders::add);
        return orders;
    }

    private static List<Integer> ids(List<JsonNode> orders) {
        return orders.stream().map(order -> order.path("id").asInt()).toList();
    }
}
