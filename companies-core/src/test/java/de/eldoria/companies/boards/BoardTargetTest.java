/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards;

import de.eldoria.companies.commands.company.order.search.SearchQuery;
import de.eldoria.companies.data.wrapper.offer.OfferMark;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.orders.query.OrderFilter;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardTargetTest {
    private static final LocalDateTime ENDS = LocalDateTime.of(2026, 11, 1, 12, 0);

    @Test
    void aBoardOfAServerEventShowsTheChunksOfItsOffersOnly() {
        assertEquals(12, BoardTarget.serverEvent(" Event:12 "));
        assertNull(BoardTarget.serverEvent("event 12"));
        assertNull(BoardTarget.serverEvent("event:none"));
        assertNull(BoardTarget.serverEvent(null));

        var target = new BoardTarget(null, OrderFilter.ALL, "event:12", null, 12);
        assertEquals("event:12", target.spec());
        var query = target.query();
        assertEquals(12, query.serverEvent());
        assertTrue(query.showsOffer(new OfferMark(3, "Logs", ENDS, OfferState.ACTIVE, 10, 0, 12)));
        assertFalse(query.showsOffer(new OfferMark(4, "Iron", ENDS, OfferState.ACTIVE, 10, 0, 7)));
        assertFalse(query.showsOffer(new OfferMark(5, "Stone", ENDS, OfferState.ACTIVE, 10, 0)));
        assertFalse(query.showsOffer(null));
    }

    @Test
    void choosingAnOfferDropsTheServerEvent() {
        var query = new SearchQuery();
        query.serverEvent(12);
        query.offer(3);

        assertEquals(0, query.serverEvent());
        assertTrue(query.showsOffer(new OfferMark(3, "Logs", ENDS, OfferState.ACTIVE, 10, 0, 12)));
    }
}
