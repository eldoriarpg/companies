/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfirmationsTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private Instant now = Instant.parse("2026-01-01T12:00:00Z");
    private final Confirmations<String> confirmations = new Confirmations<>(() -> now);

    @Test
    void confirmReturnsTheRequestedValue() {
        confirmations.request(PLAYER, "name");

        assertEquals(Optional.of("name"), confirmations.confirm(PLAYER));
    }

    @Test
    void confirmationIsUsedOnce() {
        confirmations.request(PLAYER, "name");
        confirmations.confirm(PLAYER);

        assertTrue(confirmations.confirm(PLAYER).isEmpty());
        assertFalse(confirmations.isPending(PLAYER));
    }

    @Test
    void confirmationExpiresAfterThirtySeconds() {
        confirmations.request(PLAYER, "name");
        now = now.plus(Confirmations.VALIDITY);

        assertFalse(confirmations.isPending(PLAYER));
        assertTrue(confirmations.confirm(PLAYER).isEmpty());
    }

    @Test
    void confirmationIsValidJustBeforeExpiry() {
        confirmations.request(PLAYER, "name");
        now = now.plus(Confirmations.VALIDITY).minusMillis(1);

        assertTrue(confirmations.isPending(PLAYER));
        assertEquals(Optional.of("name"), confirmations.confirm(PLAYER));
    }

    @Test
    void anotherPlayerCannotConfirm() {
        confirmations.request(PLAYER, "name");

        assertTrue(confirmations.confirm(OTHER).isEmpty());
        assertEquals(Optional.of("name"), confirmations.confirm(PLAYER));
    }

    @Test
    void newRequestReplacesThePendingOne() {
        confirmations.request(PLAYER, "first");
        confirmations.request(PLAYER, "second");

        assertEquals(Optional.of("second"), confirmations.confirm(PLAYER));
    }

    @Test
    void ticketConfirmsItsRequest() {
        var ticket = confirmations.request(PLAYER, "name");

        assertEquals(Optional.of("name"), confirmations.confirm(PLAYER, ticket));
        assertTrue(confirmations.confirm(PLAYER, ticket).isEmpty());
    }

    @Test
    void earlierTicketLeavesTheNewerRequestPending() {
        var first = confirmations.request(PLAYER, "first");
        confirmations.request(PLAYER, "second");

        assertTrue(confirmations.confirm(PLAYER, first).isEmpty());
        assertEquals(Optional.of("second"), confirmations.confirm(PLAYER));
    }

    @Test
    void ticketExpiresAfterThirtySeconds() {
        var ticket = confirmations.request(PLAYER, "name");
        now = now.plus(Confirmations.VALIDITY);

        assertTrue(confirmations.confirm(PLAYER, ticket).isEmpty());
    }

    @Test
    void ticketOfAnotherPlayerDoesNotConfirm() {
        var ticket = confirmations.request(OTHER, "other");
        confirmations.request(PLAYER, "name");

        assertTrue(confirmations.confirm(PLAYER, ticket).isEmpty());
        assertTrue(confirmations.isPending(PLAYER));
    }
}
