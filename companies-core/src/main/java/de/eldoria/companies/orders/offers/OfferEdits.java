/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.offers;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.data.repository.AOfferData;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferKind;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.system.SystemOrderService;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Changes the end and the budget of an offer that still runs, for the command and the dashboard alike.
 * <ul>
 *     <li>The end may move either way, at most {@value GoalDraft#MAX_HOURS} hours after now or, for an offer that
 *     waits, after its start. An end in the past closes the offer at the next check, as when its end passes; an offer
 *     that never started can not end before its start, cancel it instead. Offers of a server event follow their event
 *     and can not be changed.</li>
 *     <li>The budget never falls below what the offer spent, for a contest plus its prizes, and stays at most
 *     {@link GoalDraft#MAX_BUDGET}. A goal buys chunk by chunk and a minted contest only caps its payments, so their
 *     budget is only a number. A contest whose account paid its whole budget when it started pays a raise from that
 *     account at once, refused when the account can not, and gets a cut back at once.</li>
 * </ul>
 */
public final class OfferEdits {
    private static final String PREFIX = "companyadmin.offers.error.";
    private static final double TOLERANCE = 0.000_001;

    private OfferEdits() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * What came of a change.
     */
    public enum Outcome {
        /**
         * The offer is changed.
         */
        DONE,
        /**
         * There is no such offer.
         */
        UNKNOWN,
        /**
         * The offer closed already.
         */
        CLOSED,
        /**
         * The offer belongs to a server event and follows it.
         */
        LINKED,
        /**
         * The budget is below what the offer spent and its prizes.
         */
        TOO_LOW,
        /**
         * The budget is not above 0.
         */
        NOT_POSITIVE,
        /**
         * The end is before the start of an offer that never started.
         */
        BEFORE_START,
        /**
         * The end or budget is above the highest allowed.
         */
        TOO_HIGH,
        /**
         * The account could not pay the raise.
         */
        CANNOT_PAY,
        /**
         * The offer changed meanwhile; nothing was changed.
         */
        CHANGED
    }

    /**
     * Moves the end of an offer.
     *
     * @param data offer repository
     * @param id   id of the offer
     * @param ends the new end in UTC
     * @param now  the current time in UTC
     * @return what came of it
     */
    public static Outcome end(AOfferData data, int id, LocalDateTime ends, LocalDateTime now) {
        var offer = data.offer(id);
        if (offer.isEmpty()) return Outcome.UNKNOWN;
        if (offer.get().state().closed()) return Outcome.CLOSED;
        if (offer.get().event() != null) return Outcome.LINKED;
        var starts = offer.get().starts();
        var waiting = offer.get().started() == null;
        var from = waiting && starts.isAfter(now) ? starts : now;
        if (ends.isAfter(from.plusHours(GoalDraft.MAX_HOURS))) return Outcome.TOO_HIGH;
        if (waiting && !ends.isAfter(starts)) return Outcome.BEFORE_START;
        return data.times(id, starts, stored(ends, now)) ? Outcome.DONE : Outcome.CLOSED;
    }

    /**
     * The end that is stored for a requested one: an end in the past becomes now.
     *
     * @param ends the requested end in UTC
     * @param now  the current time in UTC
     * @return the stored end
     */
    public static LocalDateTime stored(LocalDateTime ends, LocalDateTime now) {
        return ends.isBefore(now) ? now : ends;
    }

    /**
     * The least budget an offer may be given: what it spent, for a contest plus its prizes.
     *
     * @param offer the offer
     * @return the least budget
     */
    public static double floor(EventOffer offer) {
        var spent = BigDecimal.valueOf(Math.max(0, offer.spent()));
        if (offer.kind() == OfferKind.CONTEST) spent = spent.add(BigDecimal.valueOf(offer.prizeTotal()));
        return spent.doubleValue();
    }

    /**
     * Changes the budget of an offer.
     *
     * @param data    offer repository
     * @param funding moves the money of a contest's account
     * @param id      id of the offer
     * @param budget  the new budget
     * @return what came of it
     */
    public static Outcome budget(AOfferData data, SystemOrderService.Funding funding, int id, double budget) {
        if (!Double.isFinite(budget) || budget > GoalDraft.MAX_BUDGET) return Outcome.TOO_HIGH;
        var found = data.offer(id);
        if (found.isEmpty()) return Outcome.UNKNOWN;
        var offer = found.get();
        if (offer.state().closed()) return Outcome.CLOSED;
        if (budget <= 0) return Outcome.NOT_POSITIVE;
        if (budget < floor(offer) - TOLERANCE) return Outcome.TOO_LOW;
        var delta = BigDecimal.valueOf(budget)
                .subtract(BigDecimal.valueOf(offer.budget()))
                .doubleValue();
        if (delta == 0) return Outcome.DONE;
        var maxSpent = offer.kind() == OfferKind.CONTEST
                ? BigDecimal.valueOf(budget)
                                .subtract(BigDecimal.valueOf(offer.prizeTotal()))
                                .doubleValue()
                        + TOLERANCE
                : budget + TOLERANCE;
        if (holds(offer) && holdingAccount(offer) == null) return Outcome.CANNOT_PAY;
        var account = holdingAccount(offer);
        if (account == null) {
            return data.budget(id, offer.budget(), offer.held(), budget, offer.held(), maxSpent)
                    ? Outcome.DONE
                    : Outcome.CHANGED;
        }
        var held =
                BigDecimal.valueOf(offer.held()).add(BigDecimal.valueOf(delta)).doubleValue();
        if (delta > 0) {
            if (!funding.withdraw(account, delta)) return Outcome.CANNOT_PAY;
            if (data.budget(id, offer.budget(), offer.held(), budget, held, maxSpent)) return Outcome.DONE;
            funding.deposit(account, delta);
            return Outcome.CHANGED;
        }
        if (!data.budget(id, offer.budget(), offer.held(), budget, held, maxSpent)) return Outcome.CHANGED;
        funding.deposit(account, -delta);
        return Outcome.DONE;
    }

    /**
     * Why moving the end of an offer was refused.
     *
     * @param outcome what came of it, not {@link Outcome#DONE}
     * @param id      id of the offer
     * @return the failure to show
     */
    public static CommandFailure endFailure(Outcome outcome, int id) {
        var offer = Values.number("id", id);
        return switch (outcome) {
            case UNKNOWN -> CommandFailure.of(PREFIX + "unknown", offer);
            case LINKED -> CommandFailure.of(PREFIX + "linkedTimes", offer);
            case BEFORE_START -> CommandFailure.of(PREFIX + "endBeforeStart", offer);
            case TOO_HIGH -> CommandFailure.of(PREFIX + "endHours", Values.number("max", GoalDraft.MAX_HOURS));
            default -> CommandFailure.of(PREFIX + "closed", offer);
        };
    }

    /**
     * Why changing the budget of an offer was refused.
     *
     * @param outcome what came of it, not {@link Outcome#DONE}
     * @param data    offer repository, to name the least budget
     * @param id      id of the offer
     * @return the failure to show
     */
    public static CommandFailure budgetFailure(Outcome outcome, AOfferData data, int id) {
        var offer = Values.number("id", id);
        return switch (outcome) {
            case UNKNOWN -> CommandFailure.of(PREFIX + "unknown", offer);
            case CLOSED, LINKED, BEFORE_START, DONE -> CommandFailure.of(PREFIX + "closed", offer);
            case NOT_POSITIVE -> CommandFailure.of(PREFIX + "budgetPositive");
            case TOO_HIGH -> CommandFailure.of(PREFIX + "budgetHigh", Values.number("max", GoalDraft.MAX_BUDGET));
            case TOO_LOW ->
                CommandFailure.of(
                        PREFIX + "budgetSpent",
                        offer,
                        Values.text(
                                "least",
                                BigDecimal.valueOf(data.offer(id)
                                                .map(OfferEdits::floor)
                                                .orElse(0.0))
                                        .setScale(2, RoundingMode.CEILING)
                                        .toPlainString()));
            case CANNOT_PAY -> CommandFailure.of(PREFIX + "cannotPay", offer);
            case CHANGED -> CommandFailure.of(PREFIX + "changedMeanwhile", offer);
        };
    }

    /**
     * @return whether the offer is a contest whose account paid in its whole budget when it started
     */
    private static boolean holds(EventOffer offer) {
        return offer.kind() == OfferKind.CONTEST
                && offer.source() == MoneySource.SourceType.ACCOUNT
                && offer.started() != null
                && offer.held() > 0;
    }

    /**
     * @return the account of a contest that holds its budget, null when no money is held or the account is unknown
     */
    private static @Nullable String holdingAccount(EventOffer offer) {
        if (!holds(offer)) return null;
        var account = offer.account();
        return account == null || account.isBlank() ? null : account.trim();
    }
}
