/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.events.EventSettings;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.data.repository.AOfferData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOfferData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.event.EventKind;
import de.eldoria.companies.data.wrapper.event.EventState;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.data.wrapper.offer.ContestPrize;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.GoalReward;
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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void anOfferIsPutOnAnOpenServerEventTakingItsTimes() {
        var id = goal(5);
        var now = Times.now();
        var event = event(EventState.SCHEDULED, now.plusHours(1), now.plusHours(3));
        var closed = event(EventState.ENDED, now, now.plusHours(1));
        IntFunction<Optional<ServerEvent>> events =
                found -> found == 1 ? Optional.of(event) : found == 2 ? Optional.of(closed) : Optional.empty();

        assertEquals(OffersCommand.Transition.UNKNOWN, OffersCommand.link(offers, events, id, 3));
        assertEquals(OffersCommand.Transition.REFUSED, OffersCommand.link(offers, events, id, 2));
        assertEquals(OffersCommand.Transition.DONE, OffersCommand.link(offers, events, id, 1));
        var linked = offers.offer(id).orElseThrow();
        assertEquals(1, linked.event());
        assertEquals(event.starts().withNano(0), linked.starts().withNano(0));
        assertEquals(event.ends().withNano(0), linked.ends().withNano(0));
        assertEquals(OffersCommand.Transition.DONE, OffersCommand.link(offers, events, id, null));
        assertEquals(null, offers.offer(id).orElseThrow().event());
        assertEquals(OffersCommand.Transition.UNKNOWN, OffersCommand.link(offers, events, id + 10, 1));
    }

    @Test
    void anOfferThatStartedGoesOnlyOnARunningEventAndKeepsItsTimesWhenStandalone() {
        var id = goal(0);
        offers.activate(id, 0, Times.now());
        var now = Times.now();
        var waiting = event(EventState.SCHEDULED, now.plusHours(1), now.plusHours(3));
        var running = event(EventState.ACTIVE, now.minusHours(1), now.plusHours(4));
        IntFunction<Optional<ServerEvent>> events =
                found -> found == 1 ? Optional.of(waiting) : found == 2 ? Optional.of(running) : Optional.empty();

        assertEquals(OffersCommand.Transition.EARLY, OffersCommand.link(offers, events, id, 1));
        assertEquals(null, offers.offer(id).orElseThrow().event());
        assertEquals(OffersCommand.Transition.DONE, OffersCommand.link(offers, events, id, 2));
        var linked = offers.offer(id).orElseThrow();
        assertEquals(running.ends().withNano(0), linked.ends().withNano(0));

        assertEquals(OffersCommand.Transition.DONE, OffersCommand.link(offers, events, id, null));
        var standalone = offers.offer(id).orElseThrow();
        assertEquals(linked.starts(), standalone.starts());
        assertEquals(linked.ends(), standalone.ends());
    }

    @Test
    void aRewardWhoseEventWouldBeRefusedIsNamed() {
        var id = goal(0);
        var settings = new EventSettings();
        settings.goalRewardBudget(0);

        assertEquals(
                Optional.of(ServerEvent.Problem.BUDGET),
                OffersCommand.rewardProblem(offers, id, new GoalReward(1.5, 60, null, null, null), settings, ""));
        assertEquals(
                Optional.of(ServerEvent.Problem.ACCOUNT),
                OffersCommand.rewardProblem(
                        offers, id, new GoalReward(1.5, 60, SourceType.ACCOUNT, null, 10.0), settings, ""));
        assertEquals(
                Optional.empty(),
                OffersCommand.rewardProblem(offers, id, new GoalReward(1.5, 60, null, null, 10.0), settings, ""));
    }

    @Test
    void theStateOfARewardIsNamed() {
        assertTrue(OffersCommand.rewardState(Optional.empty()).build().contains("reward.waiting"));
        assertTrue(OffersCommand.rewardState(Optional.of(AOfferData.REWARD_CREATING))
                .build()
                .contains("reward.creating"));
        assertTrue(OffersCommand.rewardState(Optional.of(AOfferData.REWARD_FAILED))
                .build()
                .contains("reward.failed"));
        assertTrue(OffersCommand.rewardState(Optional.of(12)).build().contains("reward.created"));
    }

    @Test
    void aRewardIsSetOnGoalsOnly() {
        var id = goal(0);
        var reward = OffersCommand.reward(1.5, "2h", "minted", 500.0);

        assertEquals(new GoalReward(1.5, 120, SourceType.MINTED, null, 500.0), reward);
        assertEquals(
                new GoalReward(2, 30, SourceType.ACCOUNT, "Bank", null), OffersCommand.reward(2, "30m", "Bank", null));
        assertEquals(new GoalReward(2, 30, null, null, null), OffersCommand.reward(2, "30m", null, null));
        assertEquals(OffersCommand.Transition.DONE, OffersCommand.reward(offers, id, reward));
        assertEquals(Optional.of(reward), offers.reward(id));
        assertEquals(OffersCommand.Transition.UNKNOWN, OffersCommand.reward(offers, id + 10, reward));
        assertThrows(CommandFailure.class, () -> OffersCommand.reward(1, "2h", null, null));
        assertThrows(CommandFailure.class, () -> OffersCommand.reward(1.5, "31d", null, null));
        assertThrows(CommandFailure.class, () -> OffersCommand.reward(1.5, "2h", null, 0.0));
    }

    private static ServerEvent event(EventState state, LocalDateTime starts, LocalDateTime ends) {
        return new ServerEvent(
                1,
                "Harvest",
                EventKind.TAX_REDUCTION,
                5,
                List.of(),
                List.of(),
                starts,
                ends,
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
