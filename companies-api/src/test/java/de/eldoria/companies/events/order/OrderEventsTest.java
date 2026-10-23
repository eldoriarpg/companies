/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.order;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.order.IDeliveredStack;
import de.eldoria.companies.components.order.IFullOrder;
import de.eldoria.companies.components.order.IOrderDraft;
import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.components.order.OrderSource;
import de.eldoria.companies.testing.Stubs;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderEventsTest {
    private static final UUID OWNER = UUID.randomUUID();

    private static ServerMock server;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void preOrderCreateExposesDraftForChanges() {
        var owner = server.addPlayer();
        var draft = IOrderDraft.of("order");
        draft.addLine(Material.STONE, 10, 1.0);
        var event = new PreOrderCreateEvent(owner, draft, OrderSource.PLAYER);

        event.draft().lines().getFirst().amount(5);
        event.draft().addLine(Material.DIRT, 1, 2.0);

        assertSame(owner, event.owner());
        assertEquals(OrderSource.PLAYER, event.source());
        assertEquals(6, draft.amount());
        assertEquals(7.0, draft.price());
        assertFalse(event.isAsynchronous());
    }

    @Test
    void eventsCreatedOffTheMainThreadAreAsynchronous() {
        var owner = server.addPlayer();
        var order = order(OWNER, 1);

        var event = CompletableFuture.supplyAsync(() -> new OrderCreatedEvent(order, owner, OrderSource.API))
                .join();

        assertTrue(event.isAsynchronous());
        assertSame(order, event.order());
        assertSame(owner, event.owner());
        assertEquals(OrderSource.API, event.source());
    }

    @Test
    void ownerIsLookedUpFromTheOrder() {
        var event = new OrderReceivedEvent(order(OWNER, 3), ISimpleCompany.forId(3));

        assertEquals(OWNER, event.owner().getUniqueId());
        assertEquals(3, event.company().id());
    }

    @Test
    void deliveryEventKeepsACopyOfTheDeliveries() {
        var worker = server.addPlayer();
        var company = Stubs.of(ICompanyProfile.class, Map.of("id", 3));
        var stack = Stubs.of(IDeliveredStack.class, Map.of("amount", 4));
        var deliveries = new ArrayList<IDeliveredStack>(List.of(stack));

        var event = new OrderDeliveryEvent(order(OWNER, 3), company, worker, deliveries);
        deliveries.clear();

        assertEquals(List.of(stack), event.deliveries());
        assertSame(worker, event.player());
        assertSame(company, event.company());
        assertEquals(OWNER, event.owner().getUniqueId());
    }

    @Test
    void preOrderAcceptExposesBothSides() {
        var member = server.addPlayer();
        var company = Stubs.of(ICompanyProfile.class, Map.of("id", 3));
        var order = order(OWNER, 0);

        var event = new PreOrderAcceptEvent(order, company, member);

        assertSame(order, event.order());
        assertSame(company, event.company());
        assertSame(member, event.player());
        assertEquals(OWNER, event.owner().getUniqueId());
    }

    @Test
    void removedEventCarriesItsReason() {
        var order = Stubs.of(ISimpleOrder.class, Map.of("owner", OWNER));

        assertEquals(
                OrderRemovedEvent.Reason.CANCELLED,
                new OrderRemovedEvent(order, OrderRemovedEvent.Reason.CANCELLED).reason());
        assertThrows(NullPointerException.class, () -> new OrderRemovedEvent(order, null));
    }

    @Test
    void paymentKnowsItsCompany() {
        var worker = server.addPlayer();
        var order = Stubs.of(ISimpleOrder.class, Map.of("owner", OWNER, "company", 7));
        var company = Stubs.of(ICompanyProfile.class, Map.of("id", 7));

        var withCompany = new OrderPaymentEvent(order, company, worker, 90.0, 100.0, 10.0);
        var withoutCompany = new OrderPaymentEvent(order, worker, 90.0, 100.0, 10.0);
        var untaxed = new OrderPaymentEvent(order, worker, 50.0);

        assertSame(company, withCompany.company());
        assertEquals(7, withoutCompany.company().id());
        assertEquals(90.0, withoutCompany.amount());
        assertEquals(100.0, withoutCompany.gross());
        assertEquals(10.0, withoutCompany.tax());
        assertEquals(50.0, untaxed.gross());
        assertEquals(0.0, untaxed.tax());
        assertEquals(0.0, withCompany.share());
    }

    @Test
    void paymentCarriesTheTreasuryShare() {
        var worker = server.addPlayer();
        var order = order(UUID.randomUUID(), 7);
        var company = Stubs.of(ICompanyProfile.class, Map.of("id", 7));

        var event = new OrderPaymentEvent(order, company, worker, 81.0, 100.0, 10.0, 9.0);

        assertEquals(81.0, event.amount());
        assertEquals(10.0, event.tax());
        assertEquals(9.0, event.share());
    }

    private static IFullOrder order(UUID owner, int company) {
        return Stubs.of(IFullOrder.class, Map.of("owner", owner, "company", company));
    }
}
