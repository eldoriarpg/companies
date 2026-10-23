/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.order.OrderRemovedEvent;
import de.eldoria.companies.nodes.NodeMessage.BoardsChanged;
import de.eldoria.companies.nodes.NodeMessage.PlayerPresence;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;
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
}
