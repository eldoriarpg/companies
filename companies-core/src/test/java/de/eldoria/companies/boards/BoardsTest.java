/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards;

import de.eldoria.companies.commands.company.order.search.SearchQuery;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.BoardSettings;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.orders.OrderTable;
import de.eldoria.companies.orders.query.OrderFilter;
import de.eldoria.companies.util.Permission;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardsTest {
    private static final Set<String> MATERIALS = Set.of("iron_ingot", "gold_ingot", "stone", "dirt");

    @TempDir
    Path directory;

    private ServerMock server;
    private OrderTable orders;
    private MutableClock clock;
    private Boards boards;
    private final List<SearchQuery> shown = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        server.getPluginManager()
                .addPermission(new org.bukkit.permissions.Permission(Permission.Boards.USE, PermissionDefault.TRUE));
        SqLiteTestDatabase.create(directory);
        orders = OrderTable.create();
        clock = new MutableClock(Instant.parse("2026-10-23T12:00:00Z"));
        var settings = new BoardSettings();
        boards = new Boards(
                () -> settings,
                orders.orderData(),
                part -> MATERIALS.stream().anyMatch(name -> name.contains(part)),
                clock);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void presetIsResolvedIgnoringCase() {
        var target = boards.resolve(" IRON ").orElseThrow();

        assertEquals("iron", target.preset());
        assertEquals("Iron", target.label());
        assertEquals(
                List.of("iron_ingot", "iron_block", "raw_iron"), target.filter().materials());
        assertTrue(target.filter().exactMatch());
        assertEquals("iron", target.spec());
    }

    @Test
    void materialsAreTheFallback() {
        var target = boards.resolve("Gold stone").orElseThrow();

        assertNull(target.preset());
        assertEquals("gold stone", target.label());
        assertEquals(List.of("gold", "stone"), target.filter().materials());
        assertTrue(target.filter().anyMaterial());
        assertEquals("gold stone", target.spec());
        assertEquals(target, boards.resolve(target.spec()).orElseThrow());
    }

    @Test
    void unknownBoardIsRefused() {
        assertTrue(boards.resolve("emerald").isEmpty());
        assertTrue(boards.resolve("stone emerald").isEmpty());
        assertTrue(boards.resolve("iron%").isEmpty());

        var failure = assertThrows(CommandFailure.class, () -> boards.require("emerald"));
        assertEquals("boards.error.unknown", failure.localeKey());
    }

    @Test
    void blankBoardShowsEveryOrder() {
        assertEquals(BoardTarget.ALL, boards.resolve(null).orElseThrow());
        assertEquals(BoardTarget.ALL, boards.resolve("  ").orElseThrow());
        assertEquals("", BoardTarget.ALL.spec());
        assertEquals(BoardTarget.ALL, boards.resolve(BoardTarget.ALL.spec()).orElseThrow());
    }

    @Test
    void openNeedsThePermission() {
        boards.bind((player, query) -> shown.add(query));
        var player = server.addPlayer();
        player.addAttachment(MockBukkit.createMockPlugin(), Permission.Boards.USE, false);

        var failure = assertThrows(CommandFailure.class, () -> boards.open(player, BoardTarget.ALL));

        assertEquals("boards.error.permission", failure.localeKey());
        assertTrue(shown.isEmpty());
    }

    @Test
    void openShowsTheFilterAsQuery() {
        boards.bind((player, query) -> shown.add(query));
        Player player = server.addPlayer();

        boards.open(player, boards.require("iron"));

        assertEquals(1, shown.size());
        var query = shown.getFirst();
        assertEquals(List.of("iron_ingot", "iron_block", "raw_iron"), query.materials());
        assertTrue(query.isExactMatch());
    }

    @Test
    void openIsRefusedUntilBound() {
        var player = server.addPlayer();

        var failure = assertThrows(CommandFailure.class, () -> boards.open(player, BoardTarget.ALL));

        assertEquals("error.notReady", failure.localeKey());
    }

    @Test
    void secondOpenWithinASecondIsIgnored() {
        boards.bind((player, query) -> shown.add(query));
        var player = server.addPlayer();

        boards.open(player, BoardTarget.ALL);
        boards.open(player, BoardTarget.ALL);
        clock.advance(Duration.ofSeconds(1));
        boards.open(player, BoardTarget.ALL);

        assertEquals(2, shown.size());
    }

    @Test
    void openCountCountsOpenMatchingOrders() {
        var open = orders.place(0);
        var claimed = orders.place(0);
        orders.state(claimed, OrderState.CLAIMED, 1);

        assertEquals(
                1,
                boards.openCount(OrderFilter.ALL.withMaterials(List.of("stone")))
                        .join());
        assertEquals(1, boards.openCount(OrderFilter.ALL).join());
        assertEquals(
                0,
                boards.openCount(OrderFilter.ALL.withMaterials(List.of("iron"))).join());
        assertEquals(OrderState.UNCLAIMED, orders.reload(open).state());
    }

    @Test
    void presetListenersAreTold() {
        var told = new ArrayList<String>();
        boards.onPresetsChanged(() -> told.add("signs"));

        boards.presetsChanged();

        assertEquals(List.of("signs"), told);
        assertEquals(List.of("iron"), boards.presetNames());
    }

    @Test
    void eventBoardsShowTheChunksOfAllOffersOrOfOne() {
        var all = boards.resolve("Event").orElseThrow();
        var one = boards.resolve("event 7").orElseThrow();

        assertEquals(0, all.offers());
        assertEquals("event", all.spec());
        assertEquals(SearchQuery.EventOffers.ONLY, all.query().eventOffers());
        assertEquals(7, one.offers());
        assertEquals("event 7", one.spec());
        assertEquals(7, one.query().offer());
        assertEquals(one, boards.resolve(one.spec()).orElseThrow());
        assertTrue(boards.resolve("event x").isEmpty());
        assertTrue(boards.resolve("event 0").isEmpty());
        assertNull(boards.resolve("iron").orElseThrow().offers());
    }

    @Test
    void anEventBoardCountsOnlyTheChunksOfItsOffers() {
        var plain = orders.place(0.0);
        var chunk = orders.place(0.0);
        var other = orders.place(0.0);
        var ends = java.time.LocalDateTime.of(2026, 11, 1, 12, 0);
        boards.useOffers(
                ids -> {
                    var marks = new java.util.HashMap<Integer, de.eldoria.companies.data.wrapper.offer.OfferMark>();
                    if (ids.contains(chunk.id())) {
                        marks.put(
                                chunk.id(),
                                new de.eldoria.companies.data.wrapper.offer.OfferMark(
                                        3,
                                        "Logs",
                                        ends,
                                        de.eldoria.companies.data.wrapper.offer.OfferState.ACTIVE,
                                        10,
                                        0));
                    }
                    if (ids.contains(other.id())) {
                        marks.put(
                                other.id(),
                                new de.eldoria.companies.data.wrapper.offer.OfferMark(
                                        4,
                                        "Stone",
                                        ends,
                                        de.eldoria.companies.data.wrapper.offer.OfferState.ACTIVE,
                                        10,
                                        0));
                    }
                    return marks;
                },
                offer -> offer == 0 ? 2 : offer == 3 ? 1 : 0);

        assertEquals(2, boards.openCount(boards.resolve("event").orElseThrow()).join());
        assertEquals(
                1, boards.openCount(boards.resolve("event 3").orElseThrow()).join());
        assertEquals(3, boards.openCount(BoardTarget.ALL).join());
        assertTrue(plain.id() > 0);
    }

    @Test
    void anEventBoardOpensTheSearchOfItsOffers() {
        boards.bind((player, query) -> shown.add(query));

        boards.open(server.addPlayer(), boards.resolve("event 5").orElseThrow());

        assertEquals(5, shown.getFirst().offer());
    }
}
