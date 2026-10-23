/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.eldoria.companies.nodes.NodeMessage.RankingChanged;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseBusTest {
    @Test
    void publishedMessagesNeverArriveAndTheStateStaysOff() {
        List<Object> received = new ArrayList<>();
        try (var bus = new DatabaseBus()) {
            bus.subscribe(RankingChanged.class, (node, message) -> received.add(message));
            bus.onStateChange(received::add);

            bus.publish(new RankingChanged());

            assertEquals(NodeBus.State.OFF, bus.state());
            assertFalse(bus.realtime());
        }
        assertTrue(received.isEmpty());
    }
}
