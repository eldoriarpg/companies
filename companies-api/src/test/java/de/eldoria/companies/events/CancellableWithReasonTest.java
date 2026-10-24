/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.order.IFullOrder;
import de.eldoria.companies.components.order.IOrderDraft;
import de.eldoria.companies.components.order.OrderSource;
import de.eldoria.companies.events.company.PreCompanyCreateEvent;
import de.eldoria.companies.events.components.CancellableWithReason;
import de.eldoria.companies.events.order.PreOrderAcceptEvent;
import de.eldoria.companies.events.order.PreOrderCreateEvent;
import de.eldoria.companies.testing.Stubs;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CancellableWithReasonTest {
    private static ServerMock server;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    static Stream<Supplier<CancellableWithReason>> events() {
        return Stream.of(
                () -> new PreOrderCreateEvent(server.addPlayer(), IOrderDraft.of("order"), OrderSource.API),
                () -> new PreOrderAcceptEvent(
                        Stubs.of(IFullOrder.class, Map.of()),
                        Stubs.of(ICompanyProfile.class, Map.of()),
                        server.addPlayer()),
                () -> new PreCompanyCreateEvent(server.addPlayer(), "Company", 100.0));
    }

    @ParameterizedTest
    @MethodSource("events")
    void startsNotCancelledWithoutReason(Supplier<CancellableWithReason> event) {
        var created = event.get();

        assertFalse(created.isCancelled());
        assertTrue(created.cancelReason().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("events")
    void cancelWithReasonCancelsAndKeepsReason(Supplier<CancellableWithReason> event) {
        var created = event.get();
        var reason = Component.text("Not today");

        created.cancel(reason);

        assertTrue(created.isCancelled());
        assertEquals(reason, created.cancelReason().orElseThrow());
    }

    @ParameterizedTest
    @MethodSource("events")
    void setCancelledWithoutReasonLeavesReasonEmpty(Supplier<CancellableWithReason> event) {
        var created = event.get();

        created.setCancelled(true);

        assertTrue(created.isCancelled());
        assertTrue(created.cancelReason().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("events")
    void uncancellingDropsReason(Supplier<CancellableWithReason> event) {
        var created = event.get();
        created.cancel(Component.text("Not today"));

        created.setCancelled(false);
        created.setCancelled(true);

        assertTrue(created.cancelReason().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("events")
    void laterReasonReplacesEarlierOne(Supplier<CancellableWithReason> event) {
        var created = event.get();
        var later = Component.text("Second");

        created.cancel(Component.text("First"));
        created.cancel(later);

        assertEquals(later, created.cancelReason().orElseThrow());
    }

    @ParameterizedTest
    @MethodSource("events")
    void cancelRejectsNullReason(Supplier<CancellableWithReason> event) {
        var created = event.get();

        assertThrows(NullPointerException.class, () -> created.cancel(null));
        assertFalse(created.isCancelled());
    }
}
