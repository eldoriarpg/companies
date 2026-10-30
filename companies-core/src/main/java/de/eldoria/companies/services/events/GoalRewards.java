/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import de.eldoria.companies.components.event.EventKind;
import de.eldoria.companies.configuration.elements.events.EventSettings;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.data.repository.AOfferData;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.GoalReward;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates the reward of a goal that was reached: a reward multiplier server event that starts at once, runs for the
 * reward's minutes and applies to every order. Its funding, account and budget are the goal's, or else the defaults
 * {@code goalRewardFunding}, {@code goalRewardAccount} and {@code goalRewardBudget} of {@code events.yml}; an account
 * left empty is the collector account. The event is announced like any event when the primary starts it.
 * <p>
 * Creating is claimed first by noting {@link AOfferData#REWARD_CREATING} with the goal, then the event is created and
 * its id noted, so a reward is created once. Before creating, an event already created for the goal, named by its
 * creator {@value #CREATOR} and the goal's id, is looked for and noted instead, so a reward whose id could not be noted
 * after its event was created gets no second event. A goal still noted as creating, since the server stopped in between, or
 * whose event could not be stored or failed with an error, is tried again on a later check, but only while its reward
 * would still run: the event ends at the time the goal was reached plus the reward's minutes, and once that passed the
 * reward is noted as {@link AOfferData#REWARD_FAILED}. A reward whose event is refused for another reason, such as a
 * minted one without a budget, is noted as failed at once. A failure is logged once.
 */
public final class GoalRewards implements Consumer<EventOffer> {
    /**
     * What the events of goal rewards name as their creator, followed by the goal's id.
     */
    public static final String CREATOR = "goal #";

    private final AOfferData offers;
    private final ServerEventService events;
    private final Supplier<EventSettings> settings;
    private final Supplier<String> collectorAccount;
    private final Clock clock;
    private final Logger logger;

    /**
     * @param offers           offer repository, holding the rewards
     * @param events           creates the events
     * @param settings         the current {@code events.yml}
     * @param collectorAccount the account of {@code economy.collectorAccount}
     * @param clock            clock giving the current time in UTC
     * @param logger           reports rewards that could not be created
     */
    public GoalRewards(
            AOfferData offers,
            ServerEventService events,
            Supplier<EventSettings> settings,
            Supplier<String> collectorAccount,
            Clock clock,
            Logger logger) {
        this.offers = offers;
        this.events = events;
        this.settings = settings;
        this.collectorAccount = collectorAccount;
        this.clock = clock;
        this.logger = logger;
    }

    @Override
    public void accept(EventOffer goal) {
        var noted = offers.rewardEvent(goal.id());
        if (noted.isPresent() && noted.get() != AOfferData.REWARD_CREATING) return;
        var reward = offers.reward(goal.id()).orElse(null);
        if (reward == null) return;
        var now = LocalDateTime.now(clock);
        var closed = goal.closed();
        var reached = closed == null ? now : closed;
        var ends = reached.plusMinutes(reward.minutes());
        if (!ends.isAfter(now)) {
            failed(goal, "its time passed before its event could be created");
            return;
        }
        if (!offers.claimReward(goal.id())) return;
        var existing = events.createdBy(CREATOR + goal.id());
        if (existing.isPresent()) {
            note(goal, existing.get().id());
            return;
        }
        ServerEventService.Created created;
        try {
            created = events.create(draft(goal, reward, settings.get(), collectorAccount.get(), now, ends));
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Could not create the reward of the goal #" + goal.id() + "; trying again", e);
            return;
        }
        if (!created.stored()) {
            if (created.problem() == ServerEvent.Problem.STORAGE) {
                logger.warning("The reward of the goal #" + goal.id() + " could not be stored; trying again.");
            } else {
                failed(goal, String.valueOf(created.problem()));
            }
            return;
        }
        if (!note(goal, created.id())) return;
        logger.info("The goal #" + goal.id() + " was reached; its reward is the server event #" + created.id() + ".");
        events.checkSoon();
    }

    private boolean note(EventOffer goal, int event) {
        if (offers.rewarded(goal.id(), event)) return true;
        logger.severe("The reward of the goal #" + goal.id() + " is the server event #" + event
                + ", but it could not be noted with the goal; the next check notes it.");
        return false;
    }

    private void failed(EventOffer goal, String why) {
        if (offers.rewarded(goal.id(), AOfferData.REWARD_FAILED)) {
            logger.warning("The reward of the goal #" + goal.id() + " is not created: " + why + ".");
        }
    }

    /**
     * The event a goal's reward creates, starting now and running for the reward's minutes, with the defaults of
     * {@code events.yml} for what the reward leaves out.
     *
     * @param goal      the goal
     * @param reward    its reward
     * @param defaults  the current {@code events.yml}
     * @param collector the account of {@code economy.collectorAccount}
     * @param now       the current time in UTC
     * @return the event
     */
    public static ServerEvent draft(
            EventOffer goal, GoalReward reward, EventSettings defaults, String collector, LocalDateTime now) {
        return draft(goal, reward, defaults, collector, now, now.plusMinutes(reward.minutes()));
    }

    /**
     * The event a goal's reward creates, with the defaults of {@code events.yml} for what the reward leaves out.
     *
     * @param goal      the goal
     * @param reward    its reward
     * @param defaults  the current {@code events.yml}
     * @param collector the account of {@code economy.collectorAccount}
     * @param now       the current time in UTC, its start
     * @param ends      its end
     * @return the event
     */
    public static ServerEvent draft(
            EventOffer goal,
            GoalReward reward,
            EventSettings defaults,
            String collector,
            LocalDateTime now,
            LocalDateTime ends) {
        var own = reward.funding();
        var funding = own == null ? defaults.goalRewardFunding() : own;
        String account = null;
        if (funding == MoneySource.SourceType.ACCOUNT) {
            var named = reward.account();
            if (named != null && !named.isBlank()) account = named;
            else account = defaults.goalRewardAccount().isEmpty() ? collector : defaults.goalRewardAccount();
        }
        var ownBudget = reward.budget();
        var budget = ownBudget != null
                ? ownBudget
                : defaults.goalRewardBudget() > 0 ? Double.valueOf(defaults.goalRewardBudget()) : null;
        var name = goal.name().length() > ServerEvent.MAX_NAME_LENGTH
                ? goal.name().substring(0, ServerEvent.MAX_NAME_LENGTH)
                : goal.name();
        return ServerEvent.draft(
                name,
                EventKind.REWARD_MULTIPLIER,
                reward.value(),
                List.of(),
                List.of(),
                now,
                ends,
                funding,
                account,
                budget,
                null,
                CREATOR + goal.id(),
                now);
    }
}
