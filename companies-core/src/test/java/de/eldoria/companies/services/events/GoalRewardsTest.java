/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import de.eldoria.companies.components.event.EventKind;
import de.eldoria.companies.configuration.elements.events.EventSettings;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.data.repository.AOfferData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOfferData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteServerEventData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.GoalReward;
import de.eldoria.companies.data.wrapper.offer.OfferLine;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.util.Times;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalRewardsTest {
    @TempDir
    Path directory;

    private final EventSettings settings = new EventSettings();
    private SqLiteOfferData offers;
    private SqLiteServerEventData events;
    private GoalRewards rewards;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        SqLiteTestDatabase.create(directory);
        offers = new SqLiteOfferData(SqLiteTestDatabase.mapper());
        events = new SqLiteServerEventData();
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        var service = new ServerEventService(events, name -> name, signal -> {}, Times.UTC, logger);
        rewards = new GoalRewards(offers, service, () -> settings, () -> "Collector", Times.UTC, logger);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aReachedGoalWithARewardCreatesAMultiplierWithTheDefaultFunding() {
        var goal = goal();
        offers.reward(goal.id(), new GoalReward(1.5, 120, null, null, null));

        rewards.accept(goal);

        var created = events.events(5).getFirst();
        assertEquals(EventKind.REWARD_MULTIPLIER, created.kind());
        assertEquals(1.5, created.value());
        assertEquals(SourceType.MINTED, created.funding());
        assertEquals(1000.0, created.budget());
        assertEquals("Harbour logs", created.name());
        assertEquals(GoalRewards.CREATOR + goal.id(), created.createdBy());
        assertEquals(120, Duration.between(created.starts(), created.ends()).toMinutes());
    }

    @Test
    void aRewardFromAnAccountWithoutOneIsPaidByTheDefaultAccountOrTheCollector() {
        var goal = goal();
        offers.reward(goal.id(), new GoalReward(2, 30, SourceType.ACCOUNT, null, 50.0));

        rewards.accept(goal);

        var created = events.events(5).getFirst();
        assertEquals("Collector", created.account());
        assertEquals(50.0, created.budget());
    }

    @Test
    void aMintedRewardWithoutABudgetIsRefusedAndCreatesNothing() {
        settings.goalRewardBudget(0);
        var goal = goal();
        offers.reward(goal.id(), new GoalReward(1.5, 60, null, null, null));

        rewards.accept(goal);

        assertTrue(events.events(5).isEmpty());
        assertEquals(Optional.of(AOfferData.REWARD_FAILED), offers.rewardEvent(goal.id()));
    }

    @Test
    void aRewardIsCreatedOnceAndNoted() {
        var goal = goal();
        offers.reward(goal.id(), new GoalReward(1.5, 60, null, null, null));

        rewards.accept(goal);
        rewards.accept(goal);

        assertEquals(1, events.events(5).size());
        assertEquals(Optional.of(events.events(5).getFirst().id()), offers.rewardEvent(goal.id()));
    }

    @Test
    void aRefusedRewardIsNotedAsFailedAndNotTriedAgain() {
        settings.goalRewardBudget(0);
        var goal = goal();
        offers.reward(goal.id(), new GoalReward(1.5, 60, null, null, null));

        rewards.accept(goal);
        settings.goalRewardBudget(100);
        rewards.accept(goal);

        assertTrue(events.events(5).isEmpty());
        assertEquals(Optional.of(AOfferData.REWARD_FAILED), offers.rewardEvent(goal.id()));
    }

    @Test
    void aRewardWhoseTimePassedFailsAndOneStillRunningEndsAtItsPlannedTime() {
        var late = goal();
        var now = LocalDateTime.now(Times.UTC);
        offers.state(late.id(), OfferState.SCHEDULED, OfferState.REACHED, now.minusHours(3));
        offers.reward(late.id(), new GoalReward(1.5, 60, null, null, null));
        var recent = goal();
        offers.state(recent.id(), OfferState.SCHEDULED, OfferState.REACHED, now.minusMinutes(20));
        offers.reward(recent.id(), new GoalReward(1.5, 60, null, null, null));

        rewards.accept(offers.offer(late.id()).orElseThrow());
        rewards.accept(offers.offer(recent.id()).orElseThrow());

        assertEquals(Optional.of(AOfferData.REWARD_FAILED), offers.rewardEvent(late.id()));
        var created = events.events(5);
        assertEquals(1, created.size());
        var planned = offers.offer(recent.id()).orElseThrow().closed().plusMinutes(60);
        assertEquals(planned.withNano(0), created.getFirst().ends().withNano(0));
    }

    @Test
    void aRewardWhoseEventCouldNotBeStoredOrWasLeftCreatingIsTriedAgain() {
        var goal = goal();
        offers.reward(goal.id(), new GoalReward(1.5, 60, null, null, null));
        var failing = new AtomicBoolean(true);
        var flaky = new SqLiteServerEventData() {
            @Override
            public Optional<Integer> create(ServerEvent event) {
                return failing.getAndSet(false) ? Optional.empty() : super.create(event);
            }
        };
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        var retrying = new GoalRewards(
                offers,
                new ServerEventService(flaky, name -> name, signal -> {}, Times.UTC, logger),
                () -> settings,
                () -> "Collector",
                Times.UTC,
                logger);

        retrying.accept(goal);
        assertEquals(Optional.of(AOfferData.REWARD_CREATING), offers.rewardEvent(goal.id()));

        retrying.accept(goal);
        var created = events.events(5);
        assertEquals(1, created.size());
        assertEquals(Optional.of(created.getFirst().id()), offers.rewardEvent(goal.id()));
    }

    @Test
    void anEventCreatedWhoseIdCouldNotBeNotedIsNotedOnTheNextCheckWithoutASecondOne() {
        var goal = goal();
        offers.reward(goal.id(), new GoalReward(1.5, 60, null, null, null));
        var failing = new AtomicBoolean(true);
        var forgetful = new SqLiteOfferData(SqLiteTestDatabase.mapper()) {
            @Override
            public boolean rewarded(int offer, int event) {
                return !failing.getAndSet(false) && super.rewarded(offer, event);
            }
        };
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        var flaky = new GoalRewards(
                forgetful,
                new ServerEventService(events, name -> name, signal -> {}, Times.UTC, logger),
                () -> settings,
                () -> "Collector",
                Times.UTC,
                logger);

        flaky.accept(goal);
        assertEquals(Optional.of(AOfferData.REWARD_CREATING), offers.rewardEvent(goal.id()));
        flaky.accept(goal);

        var created = events.events(5);
        assertEquals(1, created.size());
        assertEquals(Optional.of(created.getFirst().id()), offers.rewardEvent(goal.id()));
    }

    @Test
    void aGoalWithoutARewardCreatesNothing() {
        rewards.accept(goal());

        assertTrue(events.events(5).isEmpty());
    }

    private EventOffer goal() {
        var now = LocalDateTime.now(Times.UTC);
        var id = offers.create(EventOffer.goal(
                        "Harbour logs",
                        OfferLine.material("OAK_LOG"),
                        100,
                        1.0,
                        10,
                        1,
                        SourceType.MINTED,
                        null,
                        100,
                        now,
                        now.plusDays(1),
                        "Admin",
                        now))
                .orElseThrow();
        return offers.offer(id).orElseThrow();
    }
}
