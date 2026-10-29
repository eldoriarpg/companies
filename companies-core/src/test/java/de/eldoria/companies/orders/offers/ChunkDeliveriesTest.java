/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.offers;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOfferData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferLine;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.order.OrderDoneEvent;
import de.eldoria.companies.events.order.SoloOrderDoneEvent;
import de.eldoria.companies.orders.OrderTable;
import de.eldoria.companies.orders.SystemOwner;
import de.eldoria.companies.util.Times;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkDeliveriesTest {
    private static final Instant NOW = Instant.parse("2026-10-29T08:00:00Z");

    @TempDir
    Path directory;

    private OrderTable system;
    private SqLiteOfferData offers;
    private ChunkDeliveries deliveries;
    private int offer;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        SqLiteTestDatabase.create(directory);
        system = new OrderTable(
                new SqLiteOrderData(null, SqLiteTestDatabase.mapper()), SqLiteTestDatabase.player(SystemOwner.ID));
        offers = new SqLiteOfferData(SqLiteTestDatabase.mapper());
        deliveries = new ChunkDeliveries(
                offers, Clock.fixed(NOW, ZoneOffset.UTC), Runnable::run, Logger.getAnonymousLogger());
        var now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        offer = offers.create(EventOffer.goal(
                        "Harbour logs",
                        OfferLine.material("OAK_LOG"),
                        100,
                        1.0,
                        10,
                        1,
                        SourceType.MINTED,
                        null,
                        100,
                        now,
                        now.plusDays(1),
                        "Admin",
                        now))
                .orElseThrow();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aChunkCompletedByACompanyCountsAtOnceAndOnce() {
        var chunk = deliveredChunk(10);
        var company = new CompanyProfile(3, "Acme", Times.now(), 1, CompanyDetails.NONE, List.of());

        deliveries.onDone(new OrderDoneEvent(chunk, company));
        deliveries.onDone(new OrderDoneEvent(chunk, company));

        assertEquals(10, offers.offer(offer).orElseThrow().delivered());
    }

    @Test
    void aChunkCompletedAloneCounts() {
        var chunk = deliveredChunk(7);

        deliveries.onSoloDone(new SoloOrderDoneEvent(chunk, SqLiteTestDatabase.player(UUID.randomUUID())));

        assertEquals(7, offers.offer(offer).orElseThrow().delivered());
    }

    @Test
    void anOrderOfAPlayerIsLeftAlone() {
        var chunk = deliveredChunk(10);
        var company = new CompanyProfile(3, "Acme", Times.now(), 1, CompanyDetails.NONE, List.of());
        var now = LocalDateTime.now();
        var player =
                new SimpleOrder(chunk.id(), UUID.randomUUID(), "order", now, -1, now, OrderState.DELIVERED, 0, 0, 0);

        deliveries.onDone(new OrderDoneEvent(player, company));

        assertEquals(0, offers.offer(offer).orElseThrow().delivered());
    }

    private SimpleOrder deliveredChunk(int amount) {
        var order = system.place(0.0);
        offers.addChunk(offer, order.id(), 10, 10.0, null, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        system.deliver(order, 0, Material.OAK_LOG, UUID.randomUUID(), amount);
        system.state(order, OrderState.DELIVERED, 3);
        return system.reload(order);
    }
}
