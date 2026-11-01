/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.offers;

import de.eldoria.companies.components.offer.ContestPlace;
import de.eldoria.companies.components.offer.EventOfferKind;
import de.eldoria.companies.components.offer.EventOfferState;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.data.wrapper.offer.ContestEntry;
import de.eldoria.companies.data.wrapper.offer.ContestPrize;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferKind;
import de.eldoria.companies.data.wrapper.offer.OfferLine;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.data.wrapper.offer.PrizeState;
import de.eldoria.companies.events.offer.ContestSettledEvent;
import de.eldoria.companies.events.offer.OfferEndEvent;
import de.eldoria.companies.events.offer.OfferStartEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The API events of offers: a start, an end only for an offer that ran with the reason of its state, and a settled
 * contest's places with the prizes that reached the companies.
 */
class OfferApiEventsTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 12, 0);

    private final List<Event> fired = new ArrayList<>();
    private OfferApiEvents events;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        events = new OfferApiEvents(fired::add, Bukkit::getOfflinePlayer);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void anOfferStartsAndEndsWithTheReasonOfItsState() {
        events.started(contest(OfferState.ACTIVE, NOW));
        events.ended(contest(OfferState.DEPLETED, NOW));
        events.ended(contest(OfferState.CANCELLED, NOW));
        events.ended(contest(OfferState.ENDED, NOW));

        var start = assertInstanceOf(OfferStartEvent.class, fired.getFirst());
        assertEquals(EventOfferKind.CONTEST, start.offer().kind());
        assertEquals("OAK_LOG", start.offer().item());
        assertEquals(OptionalInt.empty(), start.offer().event());
        assertEquals(
                List.of(OfferEndEvent.Reason.BUDGET_SPENT, OfferEndEvent.Reason.CANCELLED, OfferEndEvent.Reason.ENDED),
                fired.stream()
                        .skip(1)
                        .map(OfferEndEvent.class::cast)
                        .map(OfferEndEvent::reason)
                        .toList());
    }

    @Test
    void anOfferThatNeverRanOrStillRunsFiresNoEnd() {
        events.ended(contest(OfferState.CANCELLED, null));
        events.ended(contest(OfferState.ACTIVE, NOW));
        events.ended(null);

        assertTrue(fired.isEmpty());
    }

    @Test
    void aSettledContestNamesItsPlacesWithThePrizesPaid() {
        var ranking = List.of(
                new ContestEntry(1, 7, "Miners", 60, 120, NOW, null, NOW),
                new ContestEntry(1, 8, null, 40, 80, NOW, null, NOW),
                new ContestEntry(1, 9, "Late", 10, 20, NOW, null, NOW));
        var prizes = List.of(
                new ContestPrize(1, 50, 7, "Miners", PrizeState.PAID),
                new ContestPrize(2, 20, 8, null, PrizeState.FAILED),
                new ContestPrize(3, 10, 5, "Gone", PrizeState.PAID));

        events.contestClosed(contest(OfferState.REACHED, NOW), ranking, prizes);

        var settled = assertInstanceOf(ContestSettledEvent.class, fired.getFirst());
        assertEquals(
                List.of(
                        new ContestPlace(1, 7, "Miners", 60, 50),
                        new ContestPlace(2, 8, "", 40, 0),
                        new ContestPlace(3, 9, "Late", 10, 0)),
                settled.places());
    }

    @Test
    void theApiKindsAndStatesNameEveryStoredOne() {
        assertEquals(
                Arrays.stream(OfferKind.values()).map(Enum::name).toList(),
                Arrays.stream(EventOfferKind.values()).map(Enum::name).toList());
        assertEquals(
                Arrays.stream(OfferState.values()).map(Enum::name).toList(),
                Arrays.stream(EventOfferState.values()).map(Enum::name).toList());
        for (var state : OfferState.values()) {
            assertEquals(state.closed(), EventOfferState.valueOf(state.name()).closed());
        }
    }

    @Test
    void withoutAServerNothingIsFired() {
        OfferApiEvents.NONE.started(contest(OfferState.ACTIVE, NOW));
        OfferApiEvents.NONE.ended(contest(OfferState.ENDED, NOW));

        assertTrue(fired.isEmpty());
    }

    private static EventOffer contest(OfferState state, LocalDateTime started) {
        var offer = EventOffer.contest(
                "Race",
                OfferLine.material("OAK_LOG"),
                100,
                1.0,
                List.of(50.0, 20.0),
                SourceType.MINTED,
                null,
                170,
                NOW,
                NOW.plusDays(1),
                "Admin",
                NOW);
        return new EventOffer(
                1,
                offer.kind(),
                offer.name(),
                null,
                offer.line(),
                offer.amount(),
                offer.unitPrice(),
                0,
                0,
                offer.source(),
                null,
                offer.budget(),
                0,
                0,
                offer.starts(),
                offer.ends(),
                state,
                "Admin",
                NOW,
                state.closed() ? NOW : null,
                offer.prizes(),
                0,
                started,
                null,
                null,
                null);
    }
}
