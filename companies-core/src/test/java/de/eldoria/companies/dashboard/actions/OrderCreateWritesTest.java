/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.components.order.HistoryOutcome;
import de.eldoria.companies.components.order.OrderSource;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.dashboard.DashboardWorld;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.history.HistoryEntry;
import de.eldoria.companies.data.wrapper.history.HistorySnapshot;
import de.eldoria.companies.events.order.OrderCreatedEvent;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.util.Times;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Placing orders from the dashboard, against the real order creation and a SQLite database.
 */
class OrderCreateWritesTest {
    private static final Set<String> MAY_CREATE = Set.of(Permission.Orders.CREATE);
    private static final Set<String> MAY_REPEAT = Set.of(Permission.Orders.CREATE, Permission.Orders.REPEAT);
    private static final String DAILY = ",\"repetition\":{\"interval\":\"daily\",\"times\":2}";

    @TempDir
    Path directory;

    private DashboardWorld world;
    private UUID owner;

    @BeforeEach
    void setUp() throws Exception {
        world = DashboardWorld.create(directory);
        owner = world.player("Orla");
        world.wallets.put(owner, 10_000.0);
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    private ActionResult perform(String action, String args, Set<String> permissions) {
        var now = Times.now();
        var session =
                new StoredSession("session", owner, "Orla", UUID.randomUUID(), now, now, now, now, null, permissions);
        var request = new ActionRequest(
                "act",
                Protocol.VERSION,
                session.id(),
                owner,
                action,
                Protocol.mapper().readTree(args));
        return DashboardWrites.performs(action)
                ? world.writes.perform(request, session)
                : world.reads.answer(request, session, feature -> true);
    }

    private ActionResult create(String lines, Set<String> permissions) {
        return perform(Actions.ORDERS_CREATE, "{\"name\":\"Walls\",\"lines\":" + lines + "}", permissions);
    }

    private ActionResult order(String name, String lines, String extra, Set<String> permissions) {
        return perform(
                Actions.ORDERS_CREATE, "{\"name\":\"" + name + "\",\"lines\":" + lines + extra + "}", permissions);
    }

    private static String stone(int count) {
        var lines = new StringBuilder("[");
        for (var index = 0; index < count; index++) {
            if (index > 0) lines.append(',');
            lines.append("{\"item\":\"stone\",\"amount\":1,\"unitPrice\":1}");
        }
        return lines.append(']').toString();
    }

    private int series() {
        return world.seriesData.retrieveByOwner(owner, false).join().size();
    }

    private int ownOrders() {
        return world.orders
                .retrieveOrdersByPlayer(SqLiteTestDatabase.player(owner), OrderState.UNCLAIMED, OrderState.RECEIVED)
                .join()
                .size();
    }

    @Test
    void aQuoteNamesPriceAndFeeAndChargesNothing() {
        var quote = perform(
                Actions.ORDERS_QUOTE,
                "{\"name\":\"Walls\",\"lines\":[{\"item\":\"minecraft:stone\",\"amount\":64,\"unitPrice\":2}]}",
                MAY_CREATE);

        assertTrue(quote.ok(), quote.reason());
        assertEquals(128.0, quote.data().path("price").asDouble());
        assertEquals(
                quote.data().path("price").asDouble() + quote.data().path("fee").asDouble(),
                quote.data().path("total").asDouble());
        assertEquals(10_000.0, world.wallets.get(owner));
        assertEquals(0, ownOrders());
    }

    @Test
    void anOrderOfMaterialsAndADefinedItemIsPlacedAndCharged() {
        var created = create(
                "[{\"item\":\"stone\",\"amount\":64,\"unitPrice\":2},"
                        + "{\"define\":{\"material\":\"diamond_sword\",\"enchantments\":{\"sharpness\":5}},"
                        + "\"amount\":1,\"unitPrice\":500}]",
                MAY_CREATE);

        assertTrue(created.ok(), created.reason());
        assertEquals(1, ownOrders());
        assertEquals(10_000.0 - created.data().path("total").asDouble(), world.wallets.get(owner), 0.001);
        var order = world.orders
                .retrieveOrderById(created.data().path("order").asInt())
                .asFuture()
                .join()
                .orElseThrow();
        var full = world.orders.retrieveFullOrder(order).asFuture().join();
        var sword = full.contents().stream()
                .filter(line -> line.stack().getType() == Material.DIAMOND_SWORD)
                .findFirst()
                .orElseThrow();
        assertTrue(sword.exact());
        assertEquals(5, sword.stack().getEnchantmentLevel(Enchantment.SHARPNESS));
    }

    @Test
    void savedItemsAreOrderedByTheirKeyAndOnlyTheOwnersOnes() {
        var sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 3);
        world.savedItems.save(owner, sword, Optional.of("blade")).join();
        var known = perform(Actions.ORDERS_ITEMS, "{}", MAY_CREATE);
        var ref = known.data().path("saved").get(0).path("ref").asString();
        assertEquals(
                "Sharpness 3",
                known.data().path("saved").get(0).path("details").get(0).asString());

        var created = create("[{\"item\":\"" + ref + "\",\"amount\":2,\"unitPrice\":100}]", MAY_CREATE);
        assertTrue(created.ok(), created.reason());

        var stranger = world.player("Rook");
        world.savedItems.save(stranger, sword, Optional.of("theirs")).join();
        var theirs = world.savedItems.items(stranger).join().getFirst().id();
        var refused = create("[{\"item\":\"saved:" + theirs + "\",\"amount\":1,\"unitPrice\":1}]", MAY_CREATE);
        assertEquals("dashboard.item.unknownItem", refused.reason());
    }

    @Test
    void whatTheRulesForbidIsRefusedWithoutCharging() {
        assertEquals(
                "error.permission.createOrder",
                create("[{\"item\":\"stone\",\"amount\":1,\"unitPrice\":1}]", Set.of())
                        .reason());
        assertEquals(
                "dashboard.item.enchantmentLevel",
                create(
                                "[{\"define\":{\"material\":\"diamond_sword\",\"enchantments\":{\"sharpness\":9}},\"amount\":1,\"unitPrice\":1}]",
                                MAY_CREATE)
                        .reason());
        assertEquals(
                "order.draft.error.notOrderable",
                create("[{\"item\":\"bedrock\",\"amount\":1,\"unitPrice\":1}]", MAY_CREATE)
                        .reason());
        assertEquals(
                "dashboard.invalid",
                create("[{\"item\":\"stone\",\"amount\":0,\"unitPrice\":1}]", MAY_CREATE)
                        .reason());
        assertEquals(10_000.0, world.wallets.get(owner));
    }

    @Test
    void theOrderLimitComesFromTheSessionsPermissions() {
        var limitedToOne = Set.of(Permission.Orders.CREATE, Permission.Orders.LIMIT + ".1");
        assertTrue(create("[{\"item\":\"stone\",\"amount\":1,\"unitPrice\":1}]", limitedToOne)
                .ok());

        var second = create("[{\"item\":\"stone\",\"amount\":1,\"unitPrice\":1}]", limitedToOne);

        assertFalse(second.ok());
        assertEquals("order.create.error.limitReached", second.reason());
    }

    @Test
    void aRepeatingOrderNeedsTheRepeatPermission() {
        var lines = "[{\"item\":\"stone\",\"amount\":1,\"unitPrice\":1}]";
        var repetition = ",\"repetition\":{\"interval\":\"daily\",\"times\":3}";

        var refused =
                perform(Actions.ORDERS_CREATE, "{\"name\":\"Daily\",\"lines\":" + lines + repetition + "}", MAY_CREATE);
        assertEquals("error.permission.repeatOrder", refused.reason());

        var started = perform(
                Actions.ORDERS_CREATE,
                "{\"name\":\"Daily\",\"lines\":" + lines + repetition + "}",
                Set.of(Permission.Orders.CREATE, Permission.Orders.REPEAT));
        assertTrue(started.ok(), started.reason());
        assertEquals(1, ownOrders());
    }

    @Test
    void theCatalogueOffersWhatSurvivalCanGiveEachMaterial() {
        var catalogue = perform(Actions.ORDERS_CATALOGUE, "{}", MAY_CREATE).data();

        var materials = catalogue.path("materials");
        var sword = materials
                .valueStream()
                .filter(entry -> entry.path("item").path("material").asString().equals("minecraft:diamond_sword"))
                .findFirst()
                .orElseThrow();
        assertTrue(sword.path("enchantments").valueStream().anyMatch(key -> key.asString()
                .equals("minecraft:sharpness")));
        assertFalse(materials
                .valueStream()
                .anyMatch(
                        entry -> entry.path("item").path("material").asString().equals("minecraft:bedrock")));
        var sharpness = catalogue
                .path("enchantments")
                .valueStream()
                .filter(entry -> entry.path("key").asString().equals("minecraft:sharpness"))
                .findFirst()
                .orElseThrow();
        assertEquals(5, sharpness.path("max").asInt());
        assertTrue(sharpness.path("conflicts").valueStream().anyMatch(key -> key.asString()
                .equals("minecraft:smite")));
        assertEquals(16, catalogue.path("dyes").size());
    }

    @Test
    void itemTotalsBeyondAWholeNumberAreRefusedRatherThanWrapped() {
        var wrapping = create(
                "[{\"item\":\"stone\",\"amount\":2147483647,\"unitPrice\":0},"
                        + "{\"item\":\"dirt\",\"amount\":2147483647,\"unitPrice\":0},"
                        + "{\"item\":\"sand\",\"amount\":4,\"unitPrice\":0}]",
                MAY_CREATE);
        var merging = create(
                "[{\"item\":\"diamond\",\"amount\":2147483647,\"unitPrice\":1},"
                        + "{\"item\":\"diamond\",\"amount\":2,\"unitPrice\":1}]",
                MAY_CREATE);

        assertFalse(wrapping.ok());
        assertFalse(merging.ok());
        assertEquals(0, ownOrders());
        assertEquals(10_000.0, world.wallets.get(owner));
    }

    @Test
    void numbersOutOfRangeAreRefusedAsInvalid() {
        assertEquals(
                "dashboard.invalid",
                create("[{\"item\":\"stone\",\"amount\":1,\"unitPrice\":1e400}]", MAY_CREATE)
                        .reason());
        assertEquals(
                "dashboard.invalid",
                create("[{\"item\":\"stone\",\"amount\":5000000000,\"unitPrice\":1}]", MAY_CREATE)
                        .reason());
        assertEquals(
                "dashboard.invalid",
                create("[{\"item\":\"stone\",\"amount\":2.5,\"unitPrice\":1}]", MAY_CREATE)
                        .reason());
        assertEquals(
                "dashboard.invalid",
                create(
                                "[{\"define\":{\"material\":\"diamond_sword\",\"enchantments\":{\"sharpness\":5000000000}},\"amount\":1,\"unitPrice\":1}]",
                                MAY_CREATE)
                        .reason());
        assertFalse(create("[{\"item\":\"stone\",\"amount\":10,\"unitPrice\":1e308}]", MAY_CREATE)
                .ok());
        assertEquals(
                "error.integrationDraftInvalid",
                create(
                                "[{\"item\":\"stone\",\"amount\":1,\"unitPrice\":0},"
                                        + "{\"item\":\"stone\",\"amount\":1,\"unitPrice\":1e308}]",
                                MAY_CREATE)
                        .reason());
        assertEquals(
                "error.integrationDraftInvalid",
                perform(
                                Actions.ORDERS_QUOTE,
                                "{\"name\":\"Walls\",\"lines\":[{\"item\":\"stone\",\"amount\":1,\"unitPrice\":1e308},{\"item\":\"dirt\",\"amount\":1,\"unitPrice\":1e308}]}",
                                MAY_CREATE)
                        .reason());
        assertEquals(
                "dashboard.invalid",
                order(
                                "Daily",
                                stone(1),
                                ",\"repetition\":{\"interval\":\"daily\",\"times\":2.5,\"until\":\"2099-01-01\"}",
                                MAY_REPEAT)
                        .reason());
        assertEquals(10_000.0, world.wallets.get(owner));
    }

    @Test
    void linesBeyondTheLimitAreRefusedBeforeAnyItemIsLookedUp() {
        var refused = create(stone(6).replace("\"stone\"", "\"saved:1\""), MAY_CREATE);

        assertEquals("order.create.error.materialLimit", refused.reason());
    }

    @Test
    void aRepeatingOrderKeepsTheLimitsOfLinesAndItems() {
        var tooMany = order("Daily", stone(6), DAILY, MAY_REPEAT);
        var tooLarge = order("Daily", "[{\"item\":\"stone\",\"amount\":5000,\"unitPrice\":1}]", DAILY, MAY_REPEAT);

        assertEquals("order.create.error.materialLimit", tooMany.reason());
        assertEquals("order.create.error.itemLimit", tooLarge.reason());
        assertEquals(0, series());
        assertEquals(0, ownOrders());
    }

    @Test
    void aRefusedRepeatingOrderLeavesNoSeriesBehind() {
        var refused = order("x".repeat(40), stone(1), DAILY, MAY_REPEAT);

        assertEquals("error.invalidLength", refused.reason());
        assertEquals(0, series());
    }

    @Test
    void aQuoteChecksTheRepetitionToo() {
        var quote =
                perform(Actions.ORDERS_QUOTE, "{\"name\":\"Daily\",\"lines\":" + stone(1) + DAILY + "}", MAY_CREATE);

        assertEquals("error.permission.repeatOrder", quote.reason());
        assertEquals(0, series());
    }

    @Test
    void anOrderPlacedOnTheDashboardSaysSo() {
        assertTrue(create(stone(1), MAY_CREATE).ok());

        assertEquals(
                OrderSource.DASHBOARD,
                world.events.single(OrderCreatedEvent.class).source());
    }

    @Test
    void anotherPlayersEarlierItemIsUnknown() {
        var sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 3);
        var placed = Times.now();
        var entry = world.history
                .submitEntry(HistoryEntry.ofOwner(
                        1,
                        world.player("Rook"),
                        HistoryOutcome.CANCELLED,
                        "Theirs",
                        placed,
                        placed,
                        new HistorySnapshot(
                                List.of(new HistorySnapshot.Line(sword, true, 1, 0, 10)), 10, 0, 0, 0, 0, List.of())))
                .join()
                .orElseThrow();

        var refused = create("[{\"item\":\"earlier:" + entry + ":0\",\"amount\":1,\"unitPrice\":1}]", MAY_CREATE);

        assertEquals("dashboard.item.unknownItem", refused.reason());
    }
}
