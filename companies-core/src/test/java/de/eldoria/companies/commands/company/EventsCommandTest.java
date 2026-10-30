/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.components.event.EventKind;
import de.eldoria.companies.components.event.EventState;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.data.wrapper.offer.ContestEntry;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferLine;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventsCommandTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 12, 0);

    @Test
    void runningOffersAndThoseStartingWithinADayAreShown() {
        var running = goal("Running", NOW.minusHours(1));
        var soon = goal("Soon", NOW.plusHours(23));
        var later = goal("Later", NOW.plusHours(25));

        var shown = EventsCommand.shown(List.of(running.withState(), soon.offer(), later.offer()), NOW);

        assertEquals(
                List.of("Running", "Soon"), shown.stream().map(EventOffer::name).toList());
    }

    @Test
    void aCompanysPlaceCountsOnlyCompaniesThatDeliveredAndExist() {
        var ranking = List.of(
                new ContestEntry(1, 5, "Iron", 50, 50, NOW, null, NOW),
                new ContestEntry(1, 6, null, 40, 40, NOW, null, NOW),
                new ContestEntry(1, 7, "Stone", 30, 30, NOW, null, NOW),
                new ContestEntry(1, 8, "Wood", 0, 0, NOW, null, null));

        assertEquals(OptionalInt.of(1), EventsCommand.place(ranking, 5));
        assertEquals(OptionalInt.of(2), EventsCommand.place(ranking, 7));
        assertEquals(OptionalInt.empty(), EventsCommand.place(ranking, 8));
    }

    private static Goal goal(String name, LocalDateTime starts) {
        return new Goal(EventOffer.goal(
                name,
                OfferLine.material("OAK_LOG"),
                100,
                1.0,
                10,
                1,
                SourceType.MINTED,
                null,
                100,
                starts,
                starts.plusDays(2),
                "Admin",
                NOW));
    }

    /**
     * A scheduled goal, which is active once {@link #withState()} is asked for.
     */
    private record Goal(EventOffer offer) {
        EventOffer withState() {
            return new EventOffer(
                    1,
                    offer.kind(),
                    offer.name(),
                    null,
                    offer.line(),
                    offer.amount(),
                    offer.unitPrice(),
                    offer.chunkSize(),
                    offer.openChunks(),
                    offer.source(),
                    null,
                    offer.budget(),
                    0,
                    0,
                    offer.starts(),
                    offer.ends(),
                    de.eldoria.companies.data.wrapper.offer.OfferState.ACTIVE,
                    "Admin",
                    offer.created(),
                    null,
                    List.of(),
                    0,
                    null,
                    null,
                    null,
                    null);
        }
    }

    @Test
    void aGoalOpensInTheAloneSearchOnlyWithoutACompanyAndWhenWorkingAloneIsAllowed() {
        assertTrue(EventsCommand.alone(false, () -> true));
        assertFalse(EventsCommand.alone(false, () -> false));
        assertFalse(EventsCommand.alone(true, () -> {
            throw new AssertionError("not asked with a company");
        }));
    }

    @Test
    void runningServerEventsAndThoseStartingWithinADayAreShownWithTheirOffersGrouped() {
        var running = serverEvent(1, EventState.ACTIVE, NOW.minusHours(1));
        var soon = serverEvent(2, EventState.SCHEDULED, NOW.plusHours(5));
        var later = serverEvent(3, EventState.SCHEDULED, NOW.plusHours(30));

        assertEquals(List.of(running, soon), EventsCommand.shownEvents(List.of(running, soon, later), NOW));

        var offer = goal("Running", NOW.minusHours(1)).offer();
        var onEvent = new EventOffer(
                offer.id(),
                offer.kind(),
                offer.name(),
                1,
                offer.line(),
                offer.amount(),
                offer.unitPrice(),
                offer.chunkSize(),
                offer.openChunks(),
                offer.source(),
                offer.account(),
                offer.budget(),
                offer.spent(),
                offer.delivered(),
                offer.starts(),
                offer.ends(),
                offer.state(),
                offer.createdBy(),
                offer.created(),
                offer.closed(),
                offer.prizes(),
                offer.held(),
                offer.started(),
                offer.settled(),
                offer.refund(),
                offer.refunded());
        assertEquals(List.of(onEvent), EventsCommand.grouped(List.of(offer, onEvent), 1));
        assertEquals(List.of(), EventsCommand.grouped(List.of(offer, onEvent), 2));
    }

    private static ServerEvent serverEvent(int id, EventState state, LocalDateTime starts) {
        return new ServerEvent(
                id,
                "Harvest",
                EventKind.TAX_REDUCTION,
                5,
                List.of(),
                List.of(),
                starts,
                starts.plusDays(1),
                null,
                null,
                null,
                0,
                state,
                null,
                "Nora",
                starts,
                null,
                null);
    }
}
