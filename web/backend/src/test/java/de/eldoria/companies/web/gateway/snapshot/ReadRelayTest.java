/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.snapshot;

import de.eldoria.companies.web.api.Actions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadRelayTest {
    @Test
    void everyActionOfTheProtocolIsForwarded() {
        var missing = Actions.ALL.stream()
                .filter(action -> !action.equals(Actions.ORDERS_RECEIVE))
                .filter(action -> !ReadRelay.READ_ACTIONS.contains(action) && !ReadRelay.WRITE_ACTIONS.contains(action))
                .toList();

        assertEquals(List.of(), missing);
    }

    @Test
    void noActionIsBothReadAndWrite() {
        assertTrue(ReadRelay.READ_ACTIONS.stream().noneMatch(ReadRelay.WRITE_ACTIONS::contains));
    }
}
