/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.testing.Stubs;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

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

    private static ICompanyProfile company(String name) {
        return Stubs.of(ICompanyProfile.class, Map.of("id", 1, "name", name));
    }
}
