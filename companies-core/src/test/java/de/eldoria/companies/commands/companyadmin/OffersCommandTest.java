/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOfferData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.offer.ContestPrize;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferLine;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.data.wrapper.offer.PrizeState;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.orders.system.SystemOrderService;
import de.eldoria.companies.util.Times;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        offers.activate(id, 0, Times.now());

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
    void aResumedGoalThatNeverStartedWaitsForTheNextCheck() {
        var id = goal(0);

        assertEquals(OffersCommand.Transition.DONE, pause(id));
        assertEquals(OffersCommand.Transition.DONE, resume(id));
        assertEquals(OfferState.SCHEDULED, state(id));
        offers.activate(id, 0, Times.now());
        assertEquals(OffersCommand.Transition.DONE, pause(id));
        assertEquals(OffersCommand.Transition.DONE, resume(id));
        assertEquals(OfferState.ACTIVE, state(id));
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

    @Test
    void aPausedContestThatNeverStartedResumesIntoWaitingAndHasNoChunks() {
        var now = Times.now();
        var id = offers.create(EventOffer.contest(
                        "Race",
                        OfferLine.material("OAK_LOG"),
                        100,
                        1.0,
                        List.of(),
                        SourceType.MINTED,
                        null,
                        100,
                        now.minusHours(1),
                        now.plusDays(1),
                        "Admin",
                        now))
                .orElseThrow();

        assertEquals(OffersCommand.Transition.DONE, pause(id));
        assertEquals(OffersCommand.Transition.DONE, resume(id));
        assertEquals(OfferState.SCHEDULED, state(id));
        assertEquals(OffersCommand.Transition.WRONG_KIND, OffersCommand.limit(offers, id, 3));
        offers.activate(id, 0, now);
        assertEquals(OffersCommand.Transition.DONE, pause(id));
        assertEquals(OffersCommand.Transition.DONE, resume(id));
        assertEquals(OfferState.ACTIVE, state(id));
    }

    @Test
    void theOfferTextsUseNoKeyThatShadowsATag() {
        for (var key : List.of(
                "offer_name",
                "company_name",
                "rule_name",
                "offer_id",
                "offer_ends",
                "offer_percent",
                "item",
                "items",
                "time",
                "place",
                "prize",
                "prizes",
                "missing",
                "gross",
                "net",
                "held",
                "budget",
                "spent",
                "left",
                "unit",
                "starts",
                "ends",
                "by",
                "created",
                "closed",
                "delivered",
                "amount",
                "percent",
                "companies",
                "waiting",
                "taken",
                "chunk",
                "chunks",
                "open",
                "rank",
                "count",
                "account",
                "material",
                "total",
                "paid",
                "price",
                "max")) {
            assertEquals(key, Keys.require(key));
        }
    }

    @Test
    void aStuckPrizeIsNotedOnceAndAFailedOneGoesBackToTheAccountAfterTheRefund() {
        var now = Times.now();
        var id = offers.create(EventOffer.contest(
                        "Race",
                        OfferLine.material("OAK_LOG"),
                        100,
                        1.0,
                        List.of(50.0, 20.0),
                        SourceType.ACCOUNT,
                        "Bank",
                        170,
                        now.minusHours(1),
                        now.plusDays(1),
                        "Admin",
                        now))
                .orElseThrow();
        offers.activate(id, 170, now);
        offers.state(id, OfferState.ACTIVE, OfferState.ENDED, now);
        offers.settle(
                id,
                List.of(
                        new ContestPrize(1, 50.0, 1, null, PrizeState.PENDING),
                        new ContestPrize(2, 20.0, 2, null, PrizeState.PENDING)),
                now);
        offers.claimPrize(id, 1);
        offers.claimPrize(id, 2);
        offers.refund(id, 100, now);
        Map<String, Double> deposits = new HashMap<>();
        var funding = new SystemOrderService.Funding() {
            @Override
            public boolean withdraw(String account, double amount) {
                return false;
            }

            @Override
            public void deposit(String account, double amount) {
                deposits.merge(account, amount, Double::sum);
            }
        };

        assertEquals(OffersCommand.Transition.UNKNOWN, OffersCommand.resolve(offers, funding, id, 3, true));
        assertEquals(OffersCommand.Transition.UNKNOWN, OffersCommand.resolve(offers, funding, id + 1, 1, true));
        assertEquals(OffersCommand.Transition.DONE, OffersCommand.resolve(offers, funding, id, 1, true));
        assertEquals(OffersCommand.Transition.REFUSED, OffersCommand.resolve(offers, funding, id, 1, false));
        assertEquals(PrizeState.PAID, offers.prizes(id).get(0).state());
        assertTrue(deposits.isEmpty());

        assertEquals(OffersCommand.Transition.DONE, OffersCommand.resolve(offers, funding, id, 2, false));
        assertEquals(PrizeState.FAILED, offers.prizes(id).get(1).state());
        assertEquals(Map.of("Bank", 20.0), deposits);
        assertEquals(50.0, offers.offer(id).orElseThrow().spent());
    }
}
