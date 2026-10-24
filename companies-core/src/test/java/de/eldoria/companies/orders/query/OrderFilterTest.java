/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.query;

import de.eldoria.companies.commands.company.order.search.SearchQuery;
import de.eldoria.companies.commands.company.order.search.SortingType;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.TestMappers;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

class OrderFilterTest {
    private final OfflinePlayer owner = SqLiteTestDatabase.player(UUID.randomUUID());

    @TempDir
    Path directory;

    private SqLiteOrderData orderData;
    private List<FullOrder> orders;

    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        orderData = new SqLiteOrderData(null, SqLiteTestDatabase.mapper());
        save("Iron supply", line(Material.IRON_INGOT, 64, 128.0));
        save("Gold and iron", line(Material.GOLD_INGOT, 16, 160.0), line(Material.IRON_BLOCK, 4, 72.0));
        save("Stone", line(Material.STONE, 256, 25.6));
        save("Redstone kit", line(Material.REDSTONE, 32, 64.0), line(Material.STONE, 64, 6.4));
        save("big IRON", line(Material.RAW_IRON, 500, 1000.0));
        save("Logs", line(Material.OAK_LOG, 10, 10.0), line(Material.BIRCH_LOG, 10, 10.0));
        orders = orderData.toFullOrders(orderData
                .retrieveOrdersByPlayer(owner, OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                .join());
    }

    private static OrderContent line(Material material, int amount, double price) {
        return new OrderContent(0, new ItemStack(material), false, amount, price);
    }

    private void save(String name, OrderContent... lines) {
        var contents = new ArrayList<OrderContent>();
        for (var i = 0; i < lines.length; i++) {
            contents.add(new OrderContent(i, lines[i].stack(), false, lines[i].amount(), lines[i].price()));
        }
        orderData
                .submitOrder(owner, new SimpleOrder(owner.getUniqueId(), name, 0.0).toFullOrder(contents))
                .join();
    }

    private static List<OrderFilter> filters() {
        var all = OrderFilter.ALL;
        return List.of(
                all,
                all.withName("iron"),
                all.withName("IRON"),
                all.withName("stone"),
                all.withName("nothing"),
                all.withMaterials(List.of("iron")),
                all.withMaterials(List.of("IRON_INGOT")),
                all.withMaterials(List.of("iron_ingot")).withMaterialMode(true, true),
                all.withMaterials(List.of("stone")),
                all.withMaterials(List.of("red")),
                all.withMaterials(List.of("red")).withMaterialMode(true, true),
                all.withMaterials(List.of("stone")).withMaterialMode(true, true),
                all.withMaterials(List.of("gold", "iron")).withMaterialMode(false, false),
                all.withMaterials(List.of("gold_ingot", "iron_ingot")).withMaterialMode(false, true),
                all.withMaterials(List.of("gold_ingot", "iron_ingot")).withMaterialMode(true, true),
                all.withMaterials(List.of("log")).withMaterialMode(false, false),
                all.withMaterials(List.of("oak_log", "birch_log")).withMaterialMode(false, true),
                all.withPrice(64.0, 160.0),
                all.withPrice(0.0, 25.6),
                all.withPrice(1000.0, 1000.0),
                all.withSize(20, 96),
                all.withSize(256, 256),
                all.withSize(0, 20),
                all.withName("iron").withPrice(100.0, 300.0).withSize(0, 100),
                all.withMaterials(List.of("stone", "redstone"))
                        .withMaterialMode(false, true)
                        .withPrice(50.0, 100.0));
    }

    private Set<Integer> sqlMatches(OrderFilter filter) {
        return orderData
                .retrieveOrdersByQuery(SearchQuery.of(filter), OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                .join()
                .stream()
                .map(FullOrder::id)
                .collect(Collectors.toSet());
    }

    private Set<Integer> javaMatches(OrderFilter filter) {
        return orders.stream().filter(filter::matches).map(FullOrder::id).collect(Collectors.toSet());
    }

    @Test
    void javaMatchingEqualsTheSqlSearch() {
        Assertions.assertEquals(6, orders.size());
        for (var filter : filters()) {
            Assertions.assertEquals(sqlMatches(filter), javaMatches(filter), filter.toString());
        }
    }

    @Test
    void filtersSelectTheExpectedOrders() {
        Assertions.assertEquals(6, javaMatches(OrderFilter.ALL).size());
        Assertions.assertEquals(3, javaMatches(OrderFilter.ALL.withName("iron")).size());
        Assertions.assertEquals(
                2,
                javaMatches(OrderFilter.ALL.withMaterials(List.of("stone")).withMaterialMode(true, true))
                        .size());
        Assertions.assertEquals(
                1, javaMatches(OrderFilter.ALL.withMaterials(List.of("red"))).size());
        Assertions.assertEquals(
                0,
                javaMatches(OrderFilter.ALL.withMaterials(List.of("red")).withMaterialMode(true, true))
                        .size());
        Assertions.assertEquals(
                1,
                javaMatches(OrderFilter.ALL
                                .withMaterials(List.of("gold", "iron"))
                                .withMaterialMode(false, false))
                        .size());
    }

    @Test
    void searchQueryRoundTripsItsFilter() {
        var query = new SearchQuery();
        query.name("Iron");
        query.materials(new ArrayList<>(List.of("iron_ingot", "gold_ingot")));
        query.anyMaterial(false);
        query.exactMatch(true);
        query.maxPrice(500.0);
        query.minPrice(10.0);
        query.maxOrderSize(256);
        query.minOrderSize(4);
        query.sortingType(SortingType.PRICE);

        var filter = query.filter();
        var copy = SearchQuery.of(filter);

        Assertions.assertEquals(
                new OrderFilter("Iron", List.of("iron_ingot", "gold_ingot"), false, true, 10.0, 500.0, 4, 256), filter);
        Assertions.assertEquals(filter, copy.filter());
        Assertions.assertEquals(SortingType.AGE, copy.sortingType());
        copy.materials().add("stone");
        Assertions.assertEquals(3, copy.materials().size());
    }

    @Test
    void anEmptySearchIsTheFilterForAllOrders() {
        Assertions.assertEquals(OrderFilter.ALL, new SearchQuery().filter());
    }

    @Test
    void blankAndLongNamesAreNormalised() {
        Assertions.assertNull(OrderFilter.ALL.withName("  ").name());
        Assertions.assertEquals(
                OrderFilter.MAX_NAME_LENGTH,
                OrderFilter.ALL.withName("x".repeat(40)).name().length());
    }

    @Test
    void negativeBoundsCountAsZero() {
        var filter = OrderFilter.ALL.withPrice(-5, -1).withSize(-3, -2);

        Assertions.assertEquals(0.0, filter.minPrice());
        Assertions.assertEquals(0.0, filter.maxPrice());
        Assertions.assertEquals(0, filter.minSize());
        Assertions.assertEquals(0, filter.maxSize());
    }

    @Test
    void jsonRoundTripKeepsTheFilterAndIgnoresUnknownFields() throws Exception {
        var mapper = TestMappers.items();
        var filter = OrderFilter.ALL
                .withName("iron")
                .withMaterials(List.of("iron_ingot"))
                .withMaterialMode(false, true)
                .withPrice(1.5, 99.99)
                .withSize(1, 64);

        var json = mapper.writeValueAsString(filter);
        var read = mapper.readValue(json, OrderFilter.class);
        var grown = mapper.readValue(json.replaceFirst("\\{", "{\"futureField\":true,"), OrderFilter.class);

        Assertions.assertEquals(filter, read);
        Assertions.assertEquals(filter, grown);
        Assertions.assertEquals(
                OrderFilter.ALL, mapper.readValue(mapper.writeValueAsString(OrderFilter.ALL), OrderFilter.class));
    }
}
