/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.eldoria.companies.nodes.NodeMessage.ServerEventChanged;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventNetworkTest {
    private final LocalBus bus = new LocalBus(UUID.randomUUID());
    private final List<ServerEventChanged> handled = new CopyOnWriteArrayList<>();
    private final EventNetwork network = new EventNetwork(bus, handled::add).start();

    @Test
    void aChangeTravelsThroughTheBusToEveryNodeTheSenderIncluded() {
        network.changed(new ServerEventChanged(3, "ACTIVE"));

        assertEquals(List.of(new ServerEventChanged(3, "ACTIVE")), bus.published);
        assertEquals(List.of(new ServerEventChanged(3, "ACTIVE")), handled);
    }

    @Test
    void aChangeOfAnotherNodeIsHandledHere() {
        bus.receive(UUID.randomUUID(), new ServerEventChanged(4, ServerEventChanged.CHANGED));

        assertEquals(List.of(new ServerEventChanged(4, ServerEventChanged.CHANGED)), handled);
    }

    @Test
    void withoutABusThatDeliversTheChangeIsHandledHereOnly() {
        bus.change(NodeBus.State.DISCONNECTED);

        network.changed(new ServerEventChanged(5, "ENDED"));

        assertTrue(bus.published.isEmpty());
        assertEquals(List.of(new ServerEventChanged(5, "ENDED")), handled);
    }
}
