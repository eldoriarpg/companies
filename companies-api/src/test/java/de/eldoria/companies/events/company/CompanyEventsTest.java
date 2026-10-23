/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.company.TreasuryKind;
import de.eldoria.companies.testing.Stubs;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyEventsTest {
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
    void preCreateExposesNameAndPrice() {
        var player = server.addPlayer();

        var event = new PreCompanyCreateEvent(player, "Miners", 250.0);

        assertSame(player, event.player());
        assertEquals("Miners", event.name());
        assertEquals(250.0, event.price());
        assertFalse(event.isAsynchronous());
    }

    @Test
    void createExposesCompanyAndFounder() {
        var founder = server.addPlayer();
        var company = company("Miners");

        var event = new CompanyCreateEvent(company, founder);

        assertSame(company, event.company());
        assertSame(founder, event.player());
    }

    @Test
    void renameByAPlayer() {
        var actor = UUID.randomUUID();

        var event = new CompanyRenameEvent(company("Diggers"), "Miners", actor, false);

        assertEquals("Miners", event.oldName());
        assertEquals("Diggers", event.newName());
        assertEquals(actor, event.actor().orElseThrow());
        assertFalse(event.byAdmin());
    }

    @Test
    void renameByTheConsole() {
        var event = new CompanyRenameEvent(company("Diggers"), "Miners", null, true);

        assertTrue(event.actor().isEmpty());
        assertTrue(event.byAdmin());
    }

    @Test
    void ownerTransferExposesBothOwners() {
        var previous = server.addPlayer();
        var next = server.addPlayer();

        var event = new CompanyOwnerTransferEvent(company("Miners"), previous, next, false);

        assertSame(previous, event.previousOwner());
        assertSame(next, event.newOwner());
        assertFalse(event.byAdmin());
    }

    @Test
    void treasuryTransactionExposesKindAmountAndActor() {
        var actor = UUID.randomUUID();

        var withdrawal = new TreasuryTransactionEvent(
                company("Miners"), TreasuryKind.WITHDRAW, new BigDecimal("-12.50"), new BigDecimal("87.50"), actor);
        var share = new TreasuryTransactionEvent(
                company("Miners"), TreasuryKind.PAYOUT_SHARE, new BigDecimal("2.00"), new BigDecimal("89.50"), null);

        assertEquals(TreasuryKind.WITHDRAW, withdrawal.kind());
        assertEquals(new BigDecimal("-12.50"), withdrawal.amount());
        assertEquals(new BigDecimal("87.50"), withdrawal.balance());
        assertEquals(actor, withdrawal.actor().orElseThrow());
        assertTrue(share.actor().isEmpty());
        assertFalse(share.isAsynchronous());
    }

    @Test
    void treasuryKindsHaveStableIds() {
        for (var kind : TreasuryKind.values()) {
            assertSame(kind, TreasuryKind.byId(kind.id()).orElseThrow());
        }
        assertEquals(8, TreasuryKind.SHARE_CHANGED.id());
        assertTrue(TreasuryKind.byId(99).isEmpty());
        assertEquals("enums.treasuryKind.payout_share", TreasuryKind.PAYOUT_SHARE.translationKey());
    }

    private static ICompanyProfile company(String name) {
        return Stubs.of(ICompanyProfile.class, Map.of("id", 1, "name", name));
    }
}
