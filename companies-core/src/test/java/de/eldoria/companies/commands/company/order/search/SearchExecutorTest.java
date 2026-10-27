/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.ItemMatchSettings;
import de.eldoria.companies.configuration.elements.systemorders.SystemOrderSettings;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteCompanyData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.orders.ItemMatcher;
import de.eldoria.companies.orders.OrderTable;
import de.eldoria.companies.orders.SystemOwner;
import de.eldoria.companies.util.TestExecutors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchExecutorTest {
    @TempDir
    Path directory;

    private final UUID manager = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();

    private ExecutorService executor;
    private OrderTable orders;
    private SearchExecutor search;
    private final SystemOrderSettings systemOrders = new SystemOrderSettings();

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(4);
        orders = OrderTable.create();
        var companyData = new SqLiteCompanyData(executor);
        var company = companyData
                .submitCompanyCreation("Miners", JoinMode.INVITE_ONLY)
                .join();
        companyData
                .submitMemberUpdate(CompanyMember.of(company, manager, CompanyPermission.MANAGE_ORDERS.mask()))
                .join();
        var settings = new CompanySettings();
        var configuration = new Configuration(plugin) {
            @Override
            public CompanySettings companySettings() {
                return settings;
            }

            @Override
            public SystemOrderSettings systemOrderSettings() {
                return systemOrders;
            }
        };
        search = new SearchExecutor(
                orders.orderData(),
                companyData,
                configuration,
                () -> ItemMatcher.of(new ItemMatchSettings()),
                SqLiteTestDatabase::player);
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
        MockBukkit.unmock();
    }

    private static List<Integer> ids(SearchResults results) {
        return results.orders().stream().map(FullOrder::id).toList();
    }

    @Test
    void aMemberSearchesByIdWithoutBeingOnline() {
        var order = orders.place(0.0);

        var results = search.search(manager, new SearchQuery()).join();

        assertEquals(List.of(order.id()), ids(results));
        assertTrue(results.canAccept());
        assertTrue(results.companyMember());
    }

    @Test
    void theFillableFilterIsIgnoredWithoutAnInventory() {
        var order = orders.place(0.0);
        var query = new SearchQuery();
        query.fillable(true);

        assertEquals(List.of(order.id()), ids(search.search(manager, query).join()));
    }

    @Test
    void playersWithoutCompanyMayNotAccept() {
        orders.place(0.0);

        var results = search.search(outsider, new SearchQuery()).join();

        assertFalse(results.canAccept());
        assertFalse(results.companyMember());
    }

    @Test
    void whileTheServerMarksItsOrdersTheSearchShowsOrHidesThem() {
        systemOrders.markSystemOrders(true);
        var player = orders.place(0.0);
        var server = new OrderTable(orders.orderData(), SqLiteTestDatabase.player(SystemOwner.ID)).place(0.0);
        var query = new SearchQuery();

        query.serverOrders(SearchQuery.ServerOrders.HIDE);
        assertEquals(List.of(player.id()), ids(search.search(manager, query).join()));
        query.serverOrders(SearchQuery.ServerOrders.ONLY);
        assertEquals(List.of(server.id()), ids(search.search(manager, query).join()));
    }

    @Test
    void withoutMarkingTheChoiceOfServerOrdersIsIgnored() {
        var player = orders.place(0.0);
        var server = new OrderTable(orders.orderData(), SqLiteTestDatabase.player(SystemOwner.ID)).place(0.0);
        var query = new SearchQuery();
        query.serverOrders(SearchQuery.ServerOrders.HIDE);

        assertEquals(
                List.of(player.id(), server.id()),
                ids(search.search(manager, query).join()));
    }

    @Test
    void playerOrdersComeFirstAmongEqualsWhileTheServerMarksItsOrders() {
        systemOrders.markSystemOrders(true);
        var server = new OrderTable(orders.orderData(), SqLiteTestDatabase.player(SystemOwner.ID)).place(0.0);
        var player = orders.place(0.0);
        var query = new SearchQuery();
        query.sortingType(SortingType.PRICE);

        assertEquals(
                List.of(player.id(), server.id()),
                ids(search.search(manager, query).join()));
        query.asc(false);
        assertEquals(
                List.of(player.id(), server.id()),
                ids(search.search(manager, query).join()));
    }
}
