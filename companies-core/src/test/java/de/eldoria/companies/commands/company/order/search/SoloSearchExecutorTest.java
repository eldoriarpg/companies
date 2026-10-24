/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.configuration.elements.ItemMatchSettings;
import de.eldoria.companies.configuration.elements.ordersettings.SoloSettings;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteHistoryData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.events.EventRecorder;
import de.eldoria.companies.orders.HistoryRecorder;
import de.eldoria.companies.orders.ItemMatcher;
import de.eldoria.companies.orders.OrderOutcomes;
import de.eldoria.companies.orders.OrderTable;
import de.eldoria.companies.orders.SoloOrderService;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.util.TestExecutors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoloSearchExecutorTest {
    private final SoloSettings settings = new SoloSettings();

    @TempDir
    Path directory;

    private ServerMock server;
    private ExecutorService executor;
    private OrderTable orders;
    private PlayerMock player;
    private SoloSearchExecutor finder;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(8);
        orders = OrderTable.create();
        player = server.addPlayer();
        player.addAttachment(plugin, Permission.Orders.SOLO, true);
        settings.enabled(true);
        settings.maxOrderSize(0);
        var outcomes = new OrderOutcomes(
                orders.orderData(),
                new HistoryRecorder(
                        new SqLiteHistoryData(SqLiteTestDatabase.mapper(), executor), Clock.systemDefaultZone()),
                new ItemReturnService(plugin, orders.orderData()),
                new EventRecorder(),
                ISimpleCompany::forId,
                Logger.getAnonymousLogger(),
                executor);
        var service = new SoloOrderService(
                orders.orderData(),
                outcomes,
                () -> settings,
                ignored -> false,
                server::getOfflinePlayer,
                new EventRecorder(),
                Clock.systemDefaultZone(),
                executor);
        var matcher = ItemMatcher.of(new ItemMatchSettings(), Logger.getAnonymousLogger());
        finder = new SoloSearchExecutor(orders.orderData(), () -> service, () -> matcher);
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
        MockBukkit.unmock();
    }

    private SearchResults search() {
        return finder.execute(player, new SearchQuery()).join();
    }

    private static List<Integer> ids(SearchResults results) {
        return results.orders().stream().map(FullOrder::id).toList();
    }

    @Test
    void ownAndTooLargeOrdersAreHidden() {
        var open = orders.place(0.0);
        new OrderTable(orders.orderData(), player).place(0.0);
        settings.maxOrderSize(15);
        var fits = ids(search());
        settings.maxOrderSize(14);
        var tooLarge = ids(search());

        assertEquals(List.of(open.id()), fits);
        assertTrue(tooLarge.isEmpty());
    }

    @Test
    void theSoloTaxIsShownAndTheOrdersCanBeAccepted() {
        settings.taxPercent(7.5);
        orders.place(0.0);

        var results = search();

        assertEquals(7.5, results.taxPercent());
        assertTrue(results.canAccept());
        assertFalse(results.limitReached());
        assertFalse(results.companyMember());
    }

    @Test
    void aPlayerAtTheLimitGetsNoOrders() {
        var claimed = orders.place(0.0);
        orders.place(0.0);
        orders.orderData().submitSoloClaim(player.getUniqueId(), claimed, 1).join();

        var results = search();

        assertTrue(results.orders().isEmpty());
        assertTrue(results.limitReached());
    }

    @Test
    void aPlayerWhoMayNotWorkAloneSeesTheOrdersWithoutAccepting() {
        settings.enabled(false);
        orders.place(0.0);

        var results = search();

        assertEquals(1, results.orders().size());
        assertFalse(results.canAccept());
    }
}
