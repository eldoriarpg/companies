/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOfferData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferLine;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.util.Times;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OffersCommandTest {
    @TempDir
    Path directory;

    private SqLiteOfferData offers;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        SqLiteTestDatabase.create(directory);
        offers = new SqLiteOfferData(SqLiteTestDatabase.mapper());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pauseResumeAndCancelMoveOnlyFromTheirStates() {
        var id = goal(0);
        offers.state(id, OfferState.SCHEDULED, OfferState.ACTIVE, Times.now());

        assertEquals(OffersCommand.Transition.REFUSED, resume(id));
        assertEquals(OffersCommand.Transition.DONE, pause(id));
        assertEquals(OfferState.PAUSED, state(id));
        assertEquals(OffersCommand.Transition.REFUSED, pause(id));
        assertEquals(OffersCommand.Transition.DONE, resume(id));
        assertEquals(OfferState.ACTIVE, state(id));
        assertEquals(OffersCommand.Transition.DONE, cancel(id));
        assertEquals(OfferState.CANCELLED, state(id));
        assertEquals(OffersCommand.Transition.REFUSED, cancel(id));
        assertEquals(OffersCommand.Transition.UNKNOWN, cancel(id + 1));
    }

    @Test
    void theOpenChunksChangeOnlyWhileTheOfferIsNotClosed() {
        var id = goal(0);

        assertEquals(OffersCommand.Transition.DONE, OffersCommand.limit(offers, id, 5));
        assertEquals(5, offers.offer(id).orElseThrow().openChunks());
        offers.state(id, OfferState.SCHEDULED, OfferState.CANCELLED, Times.now());
        assertEquals(OffersCommand.Transition.REFUSED, OffersCommand.limit(offers, id, 7));
        assertEquals(5, offers.offer(id).orElseThrow().openChunks());
        assertEquals(OffersCommand.Transition.UNKNOWN, OffersCommand.limit(offers, id + 1, 7));
    }

    @Test
    void aResumedGoalWhoseStartIsAheadWaitsForIt() {
        var id = goal(5);

        assertEquals(OffersCommand.Transition.DONE, pause(id));
        assertEquals(OffersCommand.Transition.DONE, resume(id));
        assertEquals(OfferState.SCHEDULED, state(id));
    }

    private OffersCommand.Transition pause(int id) {
        return OffersCommand.transition(
                offers, id, List.of(OfferState.SCHEDULED, OfferState.ACTIVE), OfferState.PAUSED);
    }

    private OffersCommand.Transition resume(int id) {
        return OffersCommand.transition(offers, id, List.of(OfferState.PAUSED), OfferState.ACTIVE);
    }

    private OffersCommand.Transition cancel(int id) {
        return OffersCommand.transition(offers, id, List.copyOf(OfferState.RUNNING), OfferState.CANCELLED);
    }

    private OfferState state(int id) {
        return offers.offer(id).orElseThrow().state();
    }

    private int goal(int startHours) {
        var now = Times.now();
        var starts = now.plusHours(startHours);
        return offers.create(EventOffer.goal(
                        "Logs",
                        OfferLine.material("OAK_LOG"),
                        100,
                        1.0,
                        10,
                        1,
                        SourceType.MINTED,
                        null,
                        100,
                        starts,
                        starts.plusDays(1),
                        "Admin",
                        now))
                .orElseThrow();
    }
}
