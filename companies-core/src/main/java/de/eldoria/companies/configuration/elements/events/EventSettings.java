/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.events;

import de.eldoria.companies.configuration.elements.systemorders.MoneySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The server events, the file {@code events.yml}: how their effects combine, how they are announced, and the
 * templates the primary creates events from. The events themselves are stored in the database.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class EventSettings {
    private double maxMultiplier = 3.0;
    private boolean broadcast = true;
    private boolean joinMessage = true;
    private int createAheadHours = 24;
    private List<EventTemplate> templates = new ArrayList<>();
    private MoneySource.SourceType goalRewardFunding = MoneySource.SourceType.MINTED;
    private String goalRewardAccount = "";
    private double goalRewardBudget = 1000.0;

    public EventSettings() {}

    /**
     * The highest factor the reward multipliers of all active events together put on a payout. Multipliers multiply,
     * so two events of 2 would pay four times the payout without the cap.
     *
     * @return the cap, at least 1
     */
    public double maxMultiplier() {
        return Double.isFinite(maxMultiplier) ? Math.max(1, maxMultiplier) : 1;
    }

    public void maxMultiplier(double maxMultiplier) {
        this.maxMultiplier = maxMultiplier;
    }

    /**
     * Whether every server tells its players in chat when an event starts and ends.
     *
     * @return true when starts and ends are broadcast
     */
    public boolean broadcast() {
        return broadcast;
    }

    public void broadcast(boolean broadcast) {
        this.broadcast = broadcast;
    }

    /**
     * Whether a player who joins while events run is told about them in chat. The first time a player joins while an
     * event runs, the event is put into the player's inbox instead.
     *
     * @return true when joining players are told
     */
    public boolean joinMessage() {
        return joinMessage;
    }

    public void joinMessage(boolean joinMessage) {
        this.joinMessage = joinMessage;
    }

    /**
     * How long before its start the primary creates the next event of a template, so players see it coming.
     *
     * @return the hours, at least 0
     */
    public int createAheadHours() {
        return Math.max(0, createAheadHours);
    }

    /**
     * The templates the primary creates events from, in the order of the file.
     *
     * @return the templates
     */
    public List<EventTemplate> templates() {
        return templates == null
                ? List.of()
                : templates.stream().filter(Objects::nonNull).toList();
    }

    public void templates(List<EventTemplate> templates) {
        this.templates = new ArrayList<>(templates);
    }

    /**
     * Where the money of the reward of a reached goal comes from, unless the goal names it.
     *
     * @return the funding
     */
    public MoneySource.SourceType goalRewardFunding() {
        return goalRewardFunding == null ? MoneySource.SourceType.MINTED : goalRewardFunding;
    }

    public void goalRewardFunding(MoneySource.SourceType goalRewardFunding) {
        this.goalRewardFunding = goalRewardFunding;
    }

    /**
     * The account paying the reward of a reached goal funded from an account, unless the goal names one.
     *
     * @return the account, trimmed; empty for the collector account
     */
    public String goalRewardAccount() {
        return goalRewardAccount == null ? "" : goalRewardAccount.trim();
    }

    /**
     * What the reward of a reached goal may pay in all, unless the goal names a budget.
     *
     * @return the budget, 0 for none
     */
    public double goalRewardBudget() {
        return Math.max(0, goalRewardBudget);
    }

    public void goalRewardBudget(double goalRewardBudget) {
        this.goalRewardBudget = goalRewardBudget;
    }
}
