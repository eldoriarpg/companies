/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.company.CompanyApplicationEvent;
import de.eldoria.companies.events.company.CompanyInviteEvent;
import de.eldoria.companies.events.company.CompanyMemberPermissionEvent;
import de.eldoria.companies.events.order.OrderRemovedEvent;
import de.eldoria.companies.nodes.NodeMessage.BoardsChanged;
import de.eldoria.companies.nodes.NodeMessage.CacheInvalidate;
import de.eldoria.companies.nodes.NodeMessage.PlayerPresence;
import de.eldoria.companies.util.Times;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NodeSignalsTest {
    private final LocalBus bus = new LocalBus(UUID.randomUUID());
    private final NodeSignals signals = new NodeSignals(bus);
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void joinsAndQuitsAreAnnounced() {
        var player = server.addPlayer();

        signals.onJoin(new PlayerJoinEvent(player, Component.empty()));
        signals.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertEquals(
                List.of(
                        new PlayerPresence(player.getUniqueId(), true),
                        new PlayerPresence(player.getUniqueId(), false)),
                bus.published);
    }

    @Test
    void aRemovedOrderOutdatesTheBoards() {
        signals.onOrderRemoved(
                new OrderRemovedEvent(new SimpleOrder(UUID.randomUUID(), "Wood", 0), OrderRemovedEvent.Reason.PURGED));

        assertEquals(List.of(new BoardsChanged()), bus.published);
    }

    @Test
    void invitesAndApplicationsOutdateThePlayer() {
        var target = UUID.randomUUID();
        var applicant = UUID.randomUUID();

        signals.onInvite(new CompanyInviteEvent(company(), target, UUID.randomUUID(), Times.now()));
        signals.onApplication(new CompanyApplicationEvent(company(), applicant, "Hi"));

        assertEquals(
                List.of(
                        new CacheInvalidate(NodeCaches.PLAYER, target.toString()),
                        new CacheInvalidate(NodeCaches.PLAYER, applicant.toString())),
                bus.published);
    }

    @Test
    void changedPermissionsOutdateTheCompany() {
        var member = CompanyMember.of(7, UUID.randomUUID(), CompanyPermission.INVITE.mask());

        signals.onMemberPermission(new CompanyMemberPermissionEvent(company(), member, UUID.randomUUID(), Set.of()));

        assertEquals(List.of(new CacheInvalidate(NodeCaches.COMPANY, "7")), bus.published);
    }

    private static CompanyProfile company() {
        return new CompanyProfile(7, "Miners", Times.now(), 1, CompanyDetails.NONE, List.of());
    }
}
