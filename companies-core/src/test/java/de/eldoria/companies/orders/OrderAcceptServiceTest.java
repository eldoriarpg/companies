/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.EventRecorder;
import de.eldoria.companies.events.order.OrderAcceptEvent;
import de.eldoria.companies.events.order.PreOrderAcceptEvent;
import de.eldoria.companies.orders.OrderAcceptService.Result;
import de.eldoria.companies.util.TestExecutors;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderAcceptServiceTest {
    private static final int COMPANY = 6;

    @TempDir
    Path directory;

    private ExecutorService executor;
    private OrderTable orders;
    private Player player;
    private CompanyProfile company;
    private final EventRecorder events = new EventRecorder();
    private Consumer<PreOrderAcceptEvent> listener = event -> {};
    private OrderAcceptService service;

    @BeforeEach
    void setUp() throws Exception {
        var server = MockBukkit.mock();
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(4);
        orders = OrderTable.create();
        player = server.addPlayer();
        company = companyWith(CompanyPermission.MANAGE_ORDERS);
        service = new OrderAcceptService(
                orders.orderData(),
                CompanySettings::new,
                event -> {
                    events.accept(event);
                    if (event instanceof PreOrderAcceptEvent pre) listener.accept(pre);
                },
                executor);
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
        MockBukkit.unmock();
    }

    private CompanyProfile companyWith(CompanyPermission permission) {
        var member = CompanyMember.forCompanyId(COMPANY, player).addPermission(permission);
        return new CompanyProfile(COMPANY, "Acme", LocalDateTime.now(), 1, CompanyDetails.NONE, List.of(member));
    }

    private Result accept(int id) {
        return service.accept(player, company, id).join();
    }

    @Test
    void acceptingClaimsTheOrderAndFiresBothEventsOnce() {
        var order = orders.place(0.0);

        var accepted = assertInstanceOf(Result.Accepted.class, accept(order.id()));

        assertEquals(OrderState.CLAIMED, accepted.order().state());
        assertEquals(COMPANY, orders.reload(order).company());
        var pre = events.single(PreOrderAcceptEvent.class);
        assertSame(player, pre.player());
        assertSame(company, pre.company());
        assertEquals(order.id(), pre.order().id());
        assertTrue(pre.isAsynchronous());
        assertEquals(order.id(), events.single(OrderAcceptEvent.class).order().id());
    }

    @Test
    void cancellingWithAReasonLeavesTheOrderUnclaimed() {
        var reason = Component.text("Not on weekends");
        listener = event -> event.cancel(reason);
        var order = orders.place(0.0);

        var refused = assertInstanceOf(Result.Refused.class, accept(order.id()));

        assertEquals("error.cancelledByIntegration", refused.failure().localeKey());
        assertEquals(reason, refused.failure().message().orElseThrow());
        assertEquals(OrderState.UNCLAIMED, orders.reload(order).state());
        assertTrue(events.of(OrderAcceptEvent.class).isEmpty());
    }

    @Test
    void cancellingWithoutAReasonShowsTheDefaultError() {
        listener = event -> event.setCancelled(true);
        var order = orders.place(0.0);

        var refused = assertInstanceOf(Result.Refused.class, accept(order.id()));

        assertEquals("error.cancelledByIntegration", refused.failure().localeKey());
        assertTrue(refused.failure().message().isEmpty());
        assertTrue(refused.failure().isActionBar());
        assertEquals(OrderState.UNCLAIMED, orders.reload(order).state());
    }

    @Test
    void refusedChecksFireNoEvent() {
        company = companyWith(CompanyPermission.INVITE);
        var order = orders.place(0.0);

        var refused = assertInstanceOf(Result.Refused.class, accept(order.id()));

        assertEquals("error.permission.acceptOrder", refused.failure().localeKey());
        assertTrue(events.of(PreOrderAcceptEvent.class).isEmpty());
    }

    @Test
    void claimedOrdersAreNotAcceptedAgain() {
        var order = orders.place(0.0);
        orders.state(order, OrderState.CLAIMED, COMPANY + 1);

        var refused = assertInstanceOf(Result.Refused.class, accept(order.id()));

        assertEquals("error.orderNotClaimable", refused.failure().localeKey());
        assertTrue(events.of(PreOrderAcceptEvent.class).isEmpty());
        assertTrue(events.of(OrderAcceptEvent.class).isEmpty());
    }
}
