/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionsTest {
    @Test
    void everyActionBelongsToAFeature() {
        List<String> orphans = Actions.ALL.stream()
                .filter(action -> Actions.feature(action) == null)
                .toList();
        assertTrue(orphans.isEmpty(), "Actions without a feature: " + orphans);
    }

    @Test
    void namesAreUnique() {
        assertEquals(Actions.ALL.size(), new HashSet<>(Actions.ALL).size());
        assertEquals(Features.ALL.size(), new HashSet<>(Features.ALL).size());
    }

    @Test
    void theLongestFeatureWins() {
        assertEquals(Features.ORDERS_ACCEPT, Actions.feature(Actions.ORDERS_ACCEPT));
        assertEquals(Features.ORDERS_ACCEPT, Actions.feature(Actions.ORDERS_ABORT));
        assertEquals(Features.COMPANY_MEMBERS, Actions.feature(Actions.MEMBERS_KICK));
        assertEquals(Features.COMPANY_CREATE, Actions.feature(Actions.COMPANY_CREATE));
        assertEquals("admin.orders", Actions.feature("admin.orders.cancel"));
        assertNull(Actions.feature("orders.browsing"));
        assertNull(Actions.feature("guilds.join"));
    }
}
