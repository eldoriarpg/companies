/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.eldoria.companies.commands.company.order.search.SearchQuery;
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
        for (var table : List.of(
                "item_returns",
                "order_deliveries",
                "order_line_materials",
                "order_content",
                "order_states",
                "orders")) {
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
    void anOrderRemembersWhetherItsOwnerWasCharged() {
        var charged = place(plainLine(Material.OAK_LOG, 5, 10.0));
        orders.submitOrder(
                        owner,
                        new SimpleOrder(owner.getUniqueId(), "free", 0.0, false)
                                .toFullOrder(List.of(plainLine(Material.OAK_LOG, 5, 10.0))))
                .join();
        var uncharged = orders.retrieveOrdersByPlayer(owner, OrderState.UNCLAIMED, OrderState.RECEIVED)
                .join()
                .getLast();

        assertTrue(orders.retrieveOrderById(charged.id()).join().orElseThrow().charged());
        assertFalse(
                orders.retrieveOrderById(uncharged.id()).join().orElseThrow().charged());
        assertFalse(orders.retrieveFullOrder(uncharged).join().charged());
    }

    @Test
    void criteriaLinesStayOutOfPricesAndVolumes() {
        var delivered = place(criteriaLine(10, 1000.0), plainLine(Material.OAK_LOG, 10, 20.0));
        state(delivered, OrderState.DELIVERED);
        place(criteriaLine(64, 64.0));

        orders.refreshMaterialPrices();
        var price = orders.retrieveMaterialPrice("oak_log").join();
        var volume = orders.deliveredVolume(NOW.minusDays(1), NOW.minusHours(1), NOW.plusDays(1));
        var market = dashboard.market();

        assertEquals(2.0, price.avgPrice(), 0.001);
        assertEquals(1, price.orders());
        assertEquals(1, volume.size());
        assertEquals(10, volume.getFirst().items());
        assertEquals(10, market.stream().mapToLong(row -> row.volume()).sum());
    }

    @Test
    void demandCountsACriteriaLineForEveryMaterialItAccepts() {
        var logs = ItemCriteria.of("#minecraft:logs");
        place(new OrderContent(0, logs.template(), false, logs, 64, 64.0));
        place(plainLine(Material.OAK_LOG, 10, 50.0));

        var demand = orders.openPlayerDemand();

        assertEquals(2, demand.get("OAK_LOG").orders());
        assertEquals(74, demand.get("OAK_LOG").items());
        assertEquals(1.0, demand.get("OAK_LOG").lowestPrice(), 0.001);
        assertEquals(64, demand.get("BIRCH_LOG").items());
        assertFalse(demand.containsKey("OAK_PLANKS"));
    }

    @Test
    void criteriaWithEnchantmentConditionsAreNoDemandForThePlainItems() {
        var swords = ItemCriteria.of("DIAMOND_SWORD").withEnchantment("sharpness", 5);
        place(new OrderContent(0, swords.template(), false, swords, 1, 0.01));

        var demand = orders.openPlayerDemand();

        assertFalse(demand.containsKey("DIAMOND_SWORD"));
        assertEquals(1, search(true, "diamond_sword").size());
    }

    @Test
    void potionConditionsAreNoDemandAndFoundOnlyUnderPotions() {
        var brews = ItemCriteria.of("POTION", "OAK_LOG").withPotion("swiftness");
        place(new OrderContent(0, brews.template(), false, brews, 3, 3.0));
        var kept = ItemCriteria.of("DIAMOND_SWORD").withMinDurability(50);
        place(new OrderContent(0, kept.template(), false, kept, 2, 2.0));

        var demand = orders.openPlayerDemand();

        assertFalse(demand.containsKey("POTION"));
        assertEquals(1, search(true, "potion").size());
        assertEquals(0, search(true, "oak_log").size());
        assertEquals(2, demand.get("DIAMOND_SWORD").items());
    }

    @Test
    void deletingAnOrderRemovesItsLineMaterials() {
        var order = place(criteriaLine(10, 50.0));

        assertTrue(orders.submitAdminDeletion(order).join().isPresent());

        assertEquals(0, lineMaterialRows());
        var cancelled = place(criteriaLine(10, 50.0));
        assertTrue(orders.submitOrderDeletion(cancelled).join());
        assertEquals(0, lineMaterialRows());
    }

    private int lineMaterialRows() {
        return query("SELECT COUNT(1) AS count FROM order_line_materials")
                .single()
                .map(row -> row.getInt("count"))
                .first()
                .orElse(0);
    }

    @Test
    void searchFindsCriteriaLinesUnderEveryMaterialTheyAccept() {
        var logs = place(criteriaLine(10, 50.0));
        var stone = place(plainLine(Material.STONE, 5, 10.0));

        assertEquals(List.of(logs.id()), search(false, "birch_log"));
        assertEquals(List.of(logs.id()), search(true, "oak_log"));
        assertEquals(List.of(logs.id()), search(false, "spruce"));
        assertEquals(List.of(stone.id()), search(true, "stone"));
        assertEquals(List.of(), search(true, "oak_planks"));
    }

    private List<Integer> search(boolean exact, String material) {
        var query = new SearchQuery();
        query.materials(List.of(material));
        query.exactMatch(exact);
        return orders.retrieveOrdersByQuery(query, OrderState.UNCLAIMED, OrderState.UNCLAIMED).join().stream()
                .map(order -> order.id())
                .sorted()
                .toList();
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
