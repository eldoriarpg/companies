/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.data.wrapper.order.SystemDecision;
import de.eldoria.companies.events.order.OrderAcceptEvent;
import de.eldoria.companies.events.order.SoloOrderAcceptEvent;
import de.eldoria.companies.orders.SystemOwner;
import de.eldoria.companies.util.Times;
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
import static org.junit.jupiter.api.Assertions.assertNull;

class GuessedOutcomesTest {
    private static final Instant NOW = Instant.parse("2026-10-26T08:00:00Z");

    @TempDir
    Path directory;

    private SqLiteOrderData orderData;
    private GuessedOutcomes outcomes;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        SqLiteTestDatabase.create(directory);
        orderData = new SqLiteOrderData(null, SqLiteTestDatabase.mapper());
        outcomes = new GuessedOutcomes(
                orderData, Clock.fixed(NOW, ZoneOffset.UTC), Runnable::run, Logger.getAnonymousLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void anOrderOfTheServerTakenByACompanyIsNotedAsTaken() {
        decision(1);
        var company = new CompanyProfile(3, "Acme", Times.now(), 1, CompanyDetails.NONE, List.of());

        outcomes.onAccept(new OrderAcceptEvent(order(1, SystemOwner.ID), company));
        outcomes.onAccept(new OrderAcceptEvent(order(1, SystemOwner.ID), company));

        var noted = orderData
                .systemOutcomes("guessed", LocalDateTime.of(2000, 1, 1, 0, 0))
                .getFirst();
        assertEquals(GuessedLearning.TAKEN, noted.outcome());
        assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), noted.endedAt());
    }

    @Test
    void anOrderOfTheServerTakenAloneIsNotedAsTaken() {
        decision(2);

        outcomes.onSoloAccept(
                new SoloOrderAcceptEvent(order(2, SystemOwner.ID), SqLiteTestDatabase.player(UUID.randomUUID())));

        assertEquals(
                GuessedLearning.TAKEN,
                orderData
                        .systemOutcomes("guessed", LocalDateTime.of(2000, 1, 1, 0, 0))
                        .getFirst()
                        .outcome());
    }

    @Test
    void aPlayersOrderIsNotNoted() {
        decision(3);
        var company = new CompanyProfile(3, "Acme", Times.now(), 1, CompanyDetails.NONE, List.of());

        outcomes.onAccept(new OrderAcceptEvent(order(3, UUID.randomUUID()), company));

        assertNull(orderData
                .systemOutcomes("guessed", LocalDateTime.of(2000, 1, 1, 0, 0))
                .getFirst()
                .outcome());
    }

    private void decision(int order) {
        orderData.saveSystemDecision(
                new SystemDecision(order, "guessed", GuessedRecord.KIND, LocalDateTime.now(), null, 1, "{}"));
    }

    private static SimpleOrder order(int id, UUID owner) {
        var now = LocalDateTime.now();
        return new SimpleOrder(id, owner, "order", now, -1, now, OrderState.UNCLAIMED, 0, 0, 0);
    }
}
