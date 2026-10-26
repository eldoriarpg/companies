/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.eldoria.companies.components.order.HistoryOutcome;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.history.HistoryEntry;
import de.eldoria.companies.data.wrapper.order.DeliveredVolume;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.data.wrapper.order.SystemDecision;
import de.eldoria.companies.orders.HistoryRecorder;
import de.eldoria.companies.orders.SystemOwner;
import de.eldoria.companies.util.TestExecutors;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.sql.DataSource;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What every dialect does with the tables and queries of system orders. The database is started once per dialect and
 * emptied before each test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class SystemOrderDataContract {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 12, 0);
    private static final int COMPANY = 4;

    private DataSource dataSource;
    private ExecutorService executor;
    private AOrderData orders;
    private AHistoryData history;
    private ADashboardData dashboard;
    private OfflinePlayer player;
    private OfflinePlayer server;

    /**
     * Starts a migrated database.
     *
     * @return its data source
     */
    protected abstract DataSource start() throws Exception;

    /**
     * Stops the database.
     */
    protected abstract void stop();

    /**
     * @return the dialect's order tables
     */
    protected abstract AOrderData orders();

    /**
     * @param executor runs the history's work
     * @return the dialect's history tables
     */
    protected abstract AHistoryData history(ExecutorService executor);

    /**
     * @return the dialect's dashboard tables
     */
    protected abstract ADashboardData dashboard();

    /**
     * @return how the dialect stores times
     */
    protected abstract Adapter<LocalDateTime> time();

    @BeforeAll
    void startDatabase() throws Exception {
        MockBukkit.mock();
        dataSource = start();
        executor = Executors.newFixedThreadPool(2);
    }

    @BeforeEach
    void setUp() {
        QueryConfiguration.setDefault(QueryConfiguration.builder(dataSource).build());
        for (var table : List.of(
                "order_content",
                "order_states",
                "orders",
                "order_history",
                "system_order_decisions",
                "system_order_rest")) {
            query("DELETE FROM " + table).single().delete();
        }
        orders = orders();
        history = history(executor);
        dashboard = dashboard();
        player = SqLiteTestDatabase.player(UUID.randomUUID());
        server = SqLiteTestDatabase.player(SystemOwner.ID);
    }

    @AfterAll
    void stopDatabase() {
        TestExecutors.drain(executor);
        stop();
        MockBukkit.unmock();
    }

    @Test
    void decisionsAreStoredCountedAndRead() {
        var open = order(server, Material.IRON_INGOT, 10, 20.0);
        var claimed = order(server, Material.IRON_INGOT, 10, 20.0);
        var delivered = order(server, Material.IRON_INGOT, 10, 20.0);
        for (var order : List.of(open, claimed, delivered)) orders.systemRule(order.id(), "iron");
        state(claimed, OrderState.CLAIMED, NOW);
        state(delivered, OrderState.DELIVERED, NOW);
        state(open, OrderState.UNCLAIMED, NOW.minusHours(5));
        assertTrue(orders.saveSystemDecision(decision(open, "iron", NOW.minusDays(1), null, 50)));
        assertTrue(orders.saveSystemDecision(decision(claimed, "iron", NOW, "bank", 20)));
        assertTrue(orders.saveSystemDecision(decision(delivered, "iron", NOW.plusHours(1), null, 5)));

        assertEquals(25.0, orders.systemSpent("iron", NOW));
        assertEquals(5.0, orders.mintedSpent(NOW));
        assertEquals(NOW.plusHours(1), orders.lastSystemPlacement("iron").orElseThrow());
        assertEquals(2, orders.activeSystemOrders("iron"));
        assertEquals(
                List.of(open.id(), claimed.id()),
                orders.systemDecisions(OrderState.UNCLAIMED, OrderState.CLAIMED).stream()
                        .map(SystemDecision::order)
                        .toList());
        assertEquals(1, orders.unclaimedSystemOrders("iron", NOW.minusHours(5)).size());
        assertEquals(0, orders.unclaimedSystemOrders("iron", NOW.minusHours(6)).size());
        var stored = orders.systemDecision(claimed.id()).orElseThrow();
        assertEquals("bank", stored.account());
        assertEquals(NOW, stored.decidedAt());
    }

    @Test
    void ordersOfTheServerStayOutOfTheMarketData() {
        var system = order(server, Material.STONE, 10, 100.0);
        state(system, OrderState.DELIVERED, NOW);
        var players = order(player, Material.STONE, 1, 100.0);
        state(players, OrderState.DELIVERED, NOW);

        orders.refreshMaterialPrices();

        var price = orders.retrieveMaterialPrice("STONE").join();
        assertEquals(100.0, price.minPrice(), 0.000_1);
        var stone = dashboard.market().stream()
                .filter(row -> row.material().equals("STONE"))
                .findFirst()
                .orElseThrow();
        assertEquals(1, stone.orders());
    }

    @Test
    void companyEntriesLeaveOutTheServersOrders() {
        var recorder = new HistoryRecorder(history, Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        var players = full(order(player, Material.STONE, 1, 1.0));
        var received = full(order(server, Material.STONE, 1, 1.0));
        var decided = full(order(server, Material.DIRT, 1, 1.0));
        var kept = recorder.companyEntry(players, COMPANY, HistoryOutcome.DELIVERED, List.of())
                .join()
                .orElseThrow();
        recorder.companyEntry(received, COMPANY, HistoryOutcome.DELIVERED, List.of())
                .join();
        recorder.ownerEntry(received, HistoryOutcome.RECEIVED).join();
        recorder.companyEntry(decided, COMPANY, HistoryOutcome.DELIVERED, List.of())
                .join();
        orders.saveSystemDecision(decision(decided, "guessed", NOW, null, 1));

        var entries = history.retrieveCompanyEntries(HistoryOutcome.DELIVERED, NOW.minusDays(1), NOW.plusDays(1))
                .join();

        assertEquals(List.of(kept), entries.stream().map(HistoryEntry::id).toList());
    }

    @Test
    void thePurgeKeepsADecisionWhileItsHistoryStays() {
        var recorder = new HistoryRecorder(history, Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        var gone = order(server, Material.STONE, 1, 1.0);
        var remembered = order(server, Material.STONE, 1, 1.0);
        recorder.ownerEntry(full(remembered), HistoryOutcome.RECEIVED).join();
        orders.saveSystemDecision(decision(gone, "iron", NOW.minusDays(100), null, 1));
        orders.saveSystemDecision(decision(remembered, "iron", NOW.minusDays(100), null, 1));
        query("DELETE FROM order_content").single().delete();
        query("DELETE FROM order_states").single().delete();
        query("DELETE FROM orders").single().delete();

        history.submitPurge(NOW.minusDays(90)).join();

        assertTrue(orders.systemDecision(gone.id()).isEmpty());
        assertTrue(orders.systemDecision(remembered.id()).isPresent());
    }

    @Test
    void playerDemandLeavesOutTheServerAndClaimedOrders() {
        order(player, Material.DIRT, 10, 30.0);
        order(player, Material.DIRT, 5, 5.0);
        state(order(player, Material.DIRT, 5, 1.0), OrderState.CLAIMED, NOW);
        order(server, Material.DIRT, 100, 1.0);

        var demand = orders.openPlayerDemand().get("DIRT");

        assertEquals(2, demand.orders());
        assertEquals(15, demand.items());
        assertEquals(1.0, demand.lowestPrice(), 0.000_1);
        assertEquals(2, orders.openPlayerOrders());
    }

    @Test
    void deliveredVolumeCountsWhatPlayersGotLately() {
        state(order(player, Material.STONE, 10, 10.0), OrderState.DELIVERED, NOW.minusDays(10));
        state(order(player, Material.STONE, 4, 10.0), OrderState.RECEIVED, NOW.minusDays(1));
        state(order(player, Material.STONE, 99, 10.0), OrderState.DELIVERED, NOW.minusDays(30));
        state(order(server, Material.STONE, 50, 10.0), OrderState.DELIVERED, NOW.minusDays(1));
        state(order(player, Material.STONE, 7, 10.0), OrderState.UNCLAIMED, NOW.minusDays(1));

        var volume = orders.deliveredVolume(NOW.minusDays(14), NOW.minusDays(3), NOW);

        assertEquals(List.of(new DeliveredVolume("STONE", 2, 14, 4)), volume);
    }

    @Test
    void aMaterialRestsUntilItsTime() {
        orders.restMaterial("DIRT", NOW.plusHours(1));
        orders.restMaterial("DIRT", NOW.plusHours(2));
        orders.restMaterial("SAND", NOW.minusHours(1));

        assertEquals(Set.of("DIRT"), orders.restingMaterials(NOW.plusMinutes(90)));
        assertTrue(orders.restingMaterials(NOW.plusHours(3)).isEmpty());
    }

    @Test
    void ordersTakenByACompanyCountSinceAPointInTime() {
        var taken = order(server, Material.DIRT, 1, 1.0);
        var earlier = order(server, Material.DIRT, 1, 1.0);
        var open = order(server, Material.DIRT, 1, 1.0);
        for (var order : List.of(taken, earlier, open)) orders.systemRule(order.id(), "guessed");
        state(taken, OrderState.CLAIMED, NOW);
        state(earlier, OrderState.DELIVERED, NOW.minusDays(1));
        state(open, OrderState.UNCLAIMED, NOW);
        query("UPDATE order_states SET company = ? WHERE id IN (?, ?, ?)")
                .single(call().bind(COMPANY).bind(taken.id()).bind(earlier.id()).bind(open.id()))
                .update();

        assertEquals(1, orders.systemOrdersTaken(COMPANY, "guessed", NOW.minusHours(1)));
        assertEquals(2, orders.systemOrdersTaken(COMPANY, "guessed", NOW.minusDays(2)));
        assertEquals(0, orders.systemOrdersTaken(COMPANY + 1, "guessed", NOW.minusDays(2)));
    }

    private SimpleOrder order(OfflinePlayer owner, Material material, int amount, double price) {
        var content = new OrderContent(0, new ItemStack(material), false, amount, price);
        orders.submitOrder(owner, new SimpleOrder(owner.getUniqueId(), "test", 0.0).toFullOrder(List.of(content)))
                .join();
        var placed = orders.retrieveOrdersByPlayer(owner, OrderState.UNCLAIMED, OrderState.RECEIVED)
                .join();
        return placed.get(placed.size() - 1);
    }

    private FullOrder full(SimpleOrder order) {
        return orders.retrieveFullOrder(order).join();
    }

    private void state(SimpleOrder order, OrderState state, LocalDateTime lastUpdate) {
        query("UPDATE order_states SET state = ?, last_update = ? WHERE id = ?")
                .single(call().bind(state.stateId()).bind(lastUpdate, time()).bind(order.id()))
                .update();
    }

    private static SystemDecision decision(
            SimpleOrder order, String rule, LocalDateTime decided, String account, double paid) {
        return decision(order.id(), rule, decided, account, paid);
    }

    private static SystemDecision decision(
            FullOrder order, String rule, LocalDateTime decided, String account, double paid) {
        return decision(order.id(), rule, decided, account, paid);
    }

    private static SystemDecision decision(int order, String rule, LocalDateTime decided, String account, double paid) {
        return new SystemDecision(order, rule, "DEFINED", decided, account, paid, "{}");
    }
}
