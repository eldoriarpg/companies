/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.eldoria.companies.components.order.ItemCriteria;
import de.eldoria.companies.components.order.LineKind;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.orders.DeliveryPart;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What every dialect does with the kinds of order lines: criteria are stored and loaded, and criteria lines stay out
 * of prices, volumes and demand. The database is started once per dialect and emptied before each test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class OrderLineDataContract {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 12, 0);
    private static final int COMPANY = 7;
    private static final ItemCriteria LOGS =
            ItemCriteria.of("#minecraft:logs").withEnchantment("minecraft:unbreaking", 2);

    private DataSource dataSource;
    private AOrderData orders;
    private ADashboardData dashboard;
    private OfflinePlayer owner;
    private OfflinePlayer worker;

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
    }

    @BeforeEach
    void setUp() {
        QueryConfiguration.setDefault(QueryConfiguration.builder(dataSource).build());
        for (var table : List.of("item_returns", "order_deliveries", "order_content", "order_states", "orders")) {
            query("DELETE FROM " + table).single().delete();
        }
        orders = orders();
        orders.invalidateMaterialPriceCache();
        dashboard = dashboard();
        owner = SqLiteTestDatabase.player(UUID.randomUUID());
        worker = SqLiteTestDatabase.player(UUID.randomUUID());
    }

    @AfterAll
    void stopDatabase() {
        try {
            if (dataSource != null) stop();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void criteriaAreStoredAndLoaded() {
        var order = place(criteriaLine(10, 50.0), plainLine(Material.OAK_LOG, 5, 10.0));

        var contents = orders.retrieveFullOrder(order).join().contents();

        assertEquals(LineKind.CRITERIA, contents.get(0).kind());
        assertEquals(LOGS, contents.get(0).criteria().orElseThrow());
        assertEquals(LOGS.template().getType(), contents.get(0).material());
        assertEquals(LineKind.MATERIAL, contents.get(1).kind());
        assertTrue(contents.get(1).criteria().isEmpty());
    }

    @Test
    void criteriaLinesStayOutOfPricesVolumesAndDemand() {
        var delivered = place(criteriaLine(10, 1000.0), plainLine(Material.OAK_LOG, 10, 20.0));
        state(delivered, OrderState.DELIVERED);
        place(criteriaLine(64, 64.0));

        orders.refreshMaterialPrices();
        var price = orders.retrieveMaterialPrice("oak_log").join();
        var volume = orders.deliveredVolume(NOW.minusDays(1), NOW.minusHours(1), NOW.plusDays(1));
        var market = dashboard.market();
        var demand = orders.openPlayerDemand();

        assertEquals(2.0, price.avgPrice(), 0.001);
        assertEquals(1, volume.size());
        assertEquals(10, volume.getFirst().items());
        assertEquals(10, market.stream().mapToLong(row -> row.volume()).sum());
        assertFalse(demand.containsKey(LOGS.template().getType().name()));
    }

    @Test
    void returnedItemsKeepTheMaterialTheyHave() {
        var order = place(criteriaLine(10, 50.0));
        SqLiteTestDatabase.setState(order.id(), OrderState.CLAIMED.stateId(), COMPANY);
        orders.submitDeliveries(worker, order, List.of(new DeliveryPart(0, enchantedLog(), 4)))
                .join();

        var returned = orders.submitUnclaimOrder(order).join();

        assertTrue(returned.isPresent());
        var stacks = orders.retrieveItemReturns(worker.getUniqueId(), ReturnKind.WORKER)
                .join();
        assertEquals(1, stacks.size());
        assertEquals(Material.BIRCH_LOG, stacks.getFirst().stack().getType());
        assertEquals(4, stacks.getFirst().amount());
        var materials = query("SELECT material FROM item_returns")
                .single()
                .map(row -> row.getString("material"))
                .all();
        assertEquals(List.of("BIRCH_LOG"), materials);
    }

    private static ItemStack enchantedLog() {
        var log = new ItemStack(Material.BIRCH_LOG);
        log.addUnsafeEnchantment(Enchantment.UNBREAKING, 2);
        return log;
    }

    private static OrderContent criteriaLine(int amount, double price) {
        return new OrderContent(0, LOGS.template(), false, LOGS, amount, price);
    }

    private static OrderContent plainLine(Material material, int amount, double price) {
        return new OrderContent(1, new ItemStack(material), false, amount, price);
    }

    private SimpleOrder place(OrderContent... contents) {
        orders.submitOrder(owner, new SimpleOrder(owner.getUniqueId(), "test", 0.0).toFullOrder(List.of(contents)))
                .join();
        var placed = orders.retrieveOrdersByPlayer(owner, OrderState.UNCLAIMED, OrderState.RECEIVED)
                .join();
        return placed.getLast();
    }

    private void state(SimpleOrder order, OrderState state) {
        query("UPDATE order_states SET state = ?, last_update = ? WHERE id = ?")
                .single(call().bind(state.stateId()).bind(NOW, time()).bind(order.id()))
                .update();
    }
}
