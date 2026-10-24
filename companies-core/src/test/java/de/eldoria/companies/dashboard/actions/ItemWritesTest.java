/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.dashboard.DashboardWorld;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import de.eldoria.companies.util.Times;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dashboard actions that move items run as the commands do, with the inventory of a player online on this node.
 */
class ItemWritesTest {
    @TempDir
    Path directory;

    private DashboardWorld world;
    private PlayerMock worker;
    private UUID owner;
    private int company;

    @BeforeEach
    void setUp() throws Exception {
        world = DashboardWorld.create(directory);
        var uuid = world.player("Moss");
        worker = new PlayerMock(world.server, "Moss", uuid);
        world.server.addPlayer(worker);
        owner = world.player("Orla");
        company = world.company("Miners", Map.of(uuid, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    /**
     * Performs an action the way the router does, off the main thread, and lets the server tick until it is answered,
     * since the actions hop onto the main thread.
     */
    private ActionResult perform(UUID player, String action, String args) throws InterruptedException {
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
        var request = new ActionRequest(
                "act",
                Protocol.VERSION,
                session.id(),
                player,
                action,
                Protocol.mapper().readTree(args));
        var answer = CompletableFuture.supplyAsync(() -> world.writes.perform(request, session), world.executor);
        var deadline = System.currentTimeMillis() + 10_000;
        while (!answer.isDone()) {
            if (System.currentTimeMillis() > deadline) throw new AssertionError("The action was not answered");
            world.server.getScheduler().performOneTick();
            Thread.sleep(5);
        }
        return answer.join();
    }

    private SimpleOrder claimed() {
        var order = world.order(owner, "Walls");
        world.claim(order, company);
        return order;
    }

    private int carried(Material material) {
        var count = 0;
        for (var stack : worker.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) count += stack.getAmount();
        }
        return count;
    }

    @Test
    void aPlayerWhoIsNotOnlineHereIsToldToJoin() throws Exception {
        var order = claimed();

        var result = perform(owner, Actions.ORDERS_DELIVER_ALL, "{\"order\":" + order.id() + "}");

        assertFalse(result.ok());
        assertEquals("dashboard.notOnline", result.reason());
    }

    @Test
    void everythingCarriedIsDeliveredAndCompletesTheOrder() throws Exception {
        var order = claimed();
        worker.getInventory().addItem(new ItemStack(Material.STONE, 10), new ItemStack(Material.DIRT, 5));

        var result = perform(worker.getUniqueId(), Actions.ORDERS_DELIVER_ALL, "{\"order\":" + order.id() + "}");

        assertTrue(result.ok(), result.reason());
        assertEquals(15, result.data().path("delivered").asInt());
        assertTrue(result.data().path("done").asBoolean());
        assertEquals(java.util.List.of("all " + order.id()), world.deliveries.calls);
        assertEquals(java.util.List.of(worker.getUniqueId()), world.moved);
    }

    @Test
    void oneLineTakesUpToTheAmount() throws Exception {
        var order = claimed();
        worker.getInventory().addItem(new ItemStack(Material.STONE, 10));

        var result = perform(
                worker.getUniqueId(),
                Actions.ORDERS_DELIVER_LINE,
                "{\"order\":" + order.id() + ",\"line\":1,\"amount\":4}");

        assertTrue(result.ok(), result.reason());
        assertEquals(4, result.data().path("delivered").asInt());
        assertFalse(result.data().path("done").asBoolean());
        assertEquals(java.util.List.of("line " + order.id() + " 0 4"), world.deliveries.calls);
    }

    @Test
    void oneLineWithoutAnAmountTakesWhatItMisses() throws Exception {
        var order = claimed();

        var result =
                perform(worker.getUniqueId(), Actions.ORDERS_DELIVER_LINE, "{\"order\":" + order.id() + ",\"line\":2}");

        assertTrue(result.ok(), result.reason());
        assertEquals(java.util.List.of("line " + order.id() + " 1 " + Integer.MAX_VALUE), world.deliveries.calls);
        assertEquals(5, result.data().path("delivered").asInt());
    }

    @Test
    void anUnknownLineOrAnOrderOfAnotherCompanyIsRefused() throws Exception {
        var order = claimed();
        var foreign = world.order(owner, "Elsewhere");
        world.claim(foreign, world.company("Others", Map.of()));

        assertEquals(
                "error.unknownLine",
                perform(worker.getUniqueId(), Actions.ORDERS_DELIVER_LINE, "{\"order\":" + order.id() + ",\"line\":3}")
                        .reason());
        assertEquals(
                "error.unknownOrder",
                perform(worker.getUniqueId(), Actions.ORDERS_DELIVER_ALL, "{\"order\":" + foreign.id() + "}")
                        .reason());
    }

    @Test
    void aDeliveredOrderIsReceivedIntoTheOwnersInventory() throws Exception {
        var buyer = new PlayerMock(world.server, "Orla", owner);
        world.server.addPlayer(buyer);
        var order = claimed();
        world.deliver(order, worker.getUniqueId(), 10);
        world.state(order, OrderState.DELIVERED, company);

        var result = perform(owner, Actions.ORDERS_DELIVER_RECEIVE, "{\"order\":" + order.id() + "}");

        assertTrue(result.ok(), result.reason());
        assertEquals(0, result.data().path("waiting").asInt());
        assertTrue(buyer.getInventory().contains(Material.STONE, 10));
        assertEquals(java.util.List.of(owner), world.moved);
    }

    @Test
    void waitingItemsAreCollected() throws Exception {
        world.itemReturns
                .store(
                        worker.getUniqueId(),
                        ReturnKind.OWNER,
                        new ItemStack(Material.OAK_LOG),
                        12,
                        ItemsReturnedEvent.Reason.ORDER_EXPIRED,
                        null)
                .join();

        var result = perform(worker.getUniqueId(), Actions.ORDERS_DELIVER_COLLECT, "{}");

        assertTrue(result.ok(), result.reason());
        assertEquals(12, result.data().path("received").asInt());
        assertEquals(0, result.data().path("remaining").asInt());
        assertEquals(12, carried(Material.OAK_LOG));
    }
}
