/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.eldoria.companies.nodes.NodeMessage.OfferAnnounced;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferNetworkTest {
    private final LocalBus bus = new LocalBus(UUID.randomUUID());
    private final List<OfferAnnounced> shown = new CopyOnWriteArrayList<>();
    private final OfferNetwork network = new OfferNetwork(bus, shown::add).start();

    @Test
    void anAnnouncementTravelsThroughTheBusToEveryNodeTheSenderIncluded() {
        network.announce(new OfferAnnounced(3, "REACHED"));

        assertEquals(List.of(new OfferAnnounced(3, "REACHED")), bus.published);
        assertEquals(List.of(new OfferAnnounced(3, "REACHED")), shown);
    }

    @Test
    void anAnnouncementOfAnotherNodeIsShownHere() {
        bus.receive(UUID.randomUUID(), new OfferAnnounced(4, "CLOSED"));

        assertEquals(List.of(new OfferAnnounced(4, "CLOSED")), shown);
    }

    @Test
    void withoutABusThatDeliversTheAnnouncementIsShownHereOnly() {
        bus.change(NodeBus.State.OFF);

        network.announce(new OfferAnnounced(5, "STARTED"));

        assertTrue(bus.published.isEmpty());
        assertEquals(List.of(new OfferAnnounced(5, "STARTED")), shown);
    }
}
