/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.offers;

import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.data.repository.AOfferData;
import de.eldoria.companies.data.wrapper.offer.ContestEntry;
import de.eldoria.companies.data.wrapper.offer.ContestPrize;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.data.wrapper.offer.PrizeState;
import de.eldoria.companies.orders.offers.EventOfferService.Announcer;
import de.eldoria.companies.orders.offers.EventOfferService.Prizes;
import de.eldoria.companies.orders.offers.EventOfferService.Run;
import de.eldoria.companies.orders.system.Purchases;
import de.eldoria.companies.orders.system.SystemOrderService.Funding;
import de.eldoria.companies.orders.system.SystemOrderService.Step;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The primary's part of the contests, run by {@link EventOfferService} in its checks.
 * <p>
 * A contest starts at its start: an account pays its whole budget in then, prizes included, so deliveries and prizes
 * are always covered; an account that cannot pay leaves the contest waiting and is tried again at the next check,
 * which is logged once. A contest that runs is {@link OfferState#REACHED reached} when it misses nothing,
 * {@link OfferState#DEPLETED depleted} when its money, less the prizes, cannot pay one more item, and
 * {@link OfferState#ENDED ends} when its end passes. Deliveries close it at once when they fill it or use up its money.
 * <p>
 * Every closed contest is settled once: its companies that delivered and still exist are ranked by their delivered
 * items, and one transaction claims the settling, writes a prize row per place and reserves the money of the prizes
 * a company reached. Each prize is then claimed before it is paid, so it is paid at most once, also when a check stops
 * halfway and the next one finishes the contest; a refused prize gives its money back to the contest. At last what
 * the account paid in and was not spent goes back to it, claimed once, and the result is announced. A cancelled
 * contest pays no prizes: all its money not spent on deliveries goes back, and it is announced as cancelled. A
 * contest that never started announces nothing.
 */
final class ContestJob {
    private final AOfferData offers;
    private final Funding funding;
    private final Prizes prizes;
    private final Announcer announcer;
    private final Purchases purchases;
    private final Logger logger;

    /**
     * @param offers    offer repository
     * @param funding   moves the money of accounts
     * @param prizes    pays the prizes
     * @param announcer announces the results
     * @param purchases logs why a contest could not start, once until it changes
     * @param logger    reports failures
     */
    ContestJob(
            AOfferData offers,
            Funding funding,
            Prizes prizes,
            Announcer announcer,
            Purchases purchases,
            Logger logger) {
        this.offers = offers;
        this.funding = funding;
        this.prizes = prizes;
        this.announcer = announcer;
        this.purchases = purchases;
        this.logger = logger;
    }

    /**
     * Starts, closes and settles a contest when it is time.
     *
     * @param offer the contest
     * @param now   the current time
     * @param run   what the check did
     */
    void handle(EventOffer offer, LocalDateTime now, Run run) {
        var current = offer;
        for (var step = 0; step < 3 && !current.state().closed(); step++) {
            var next = next(current, now, run);
            if (next == null) break;
            current = next;
        }
        if (!current.state().closed()) return;
        if (current.settled() == null) settle(current, now, run);
        var settled = offers.offer(current.id()).orElse(current);
        if (settled.settled() != null && settled.refunded() == null) finish(settled, now);
    }

    /**
     * @return the contest after one change, null when nothing changed
     */
    private @Nullable EventOffer next(EventOffer offer, LocalDateTime now, Run run) {
        var ended = !now.isBefore(offer.ends());
        OfferState to = null;
        switch (offer.state()) {
            case SCHEDULED -> {
                if (ended) to = OfferState.ENDED;
                else if (!now.isBefore(offer.starts())) return start(offer, now, run);
            }
            case ACTIVE, PAUSED -> {
                if (offer.missing() == 0) to = OfferState.REACHED;
                else if (ended) to = OfferState.ENDED;
                else if (offer.state() == OfferState.ACTIVE
                        && EventOffer.affordableAfter(
                                        offer.unitPrice(), offer.delivered(), 1, offer.deliveryMoneyLeft())
                                <= 0) {
                    to = OfferState.DEPLETED;
                }
            }
            default -> {}
        }
        if (to == null || !offers.state(offer.id(), offer.state(), to, now)) return null;
        run.changed().put(offer.id(), to);
        return offers.offer(offer.id()).orElse(null);
    }

    /**
     * Starts a contest; one that ran before and was paused while waiting for its start only goes on.
     */
    private @Nullable EventOffer start(EventOffer offer, LocalDateTime now, Run run) {
        var label = "#" + offer.id();
        if (offer.started() != null) {
            if (!offers.state(offer.id(), OfferState.SCHEDULED, OfferState.ACTIVE, now)) return null;
        } else {
            var account = offer.source() == MoneySource.SourceType.ACCOUNT ? accountOf(offer) : null;
            if (offer.source() == MoneySource.SourceType.ACCOUNT && account == null) {
                purchases.note(label, Step.NO_ACCOUNT, "");
                return null;
            }
            var held = account == null ? 0 : offer.budget();
            if (account != null && !funding.withdraw(account, held)) {
                purchases.note(label, Step.CANNOT_PAY, account);
                return null;
            }
            if (!offers.activate(offer.id(), held, now)) {
                if (account != null) funding.deposit(account, held);
                return null;
            }
        }
        run.changed().put(offer.id(), OfferState.ACTIVE);
        var started = offers.offer(offer.id()).orElse(null);
        if (started != null && offer.started() == null) {
            try {
                announcer.started(started);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Could not announce the start of contest " + offer.id(), e);
            }
        }
        return started;
    }

    private static @Nullable String accountOf(EventOffer offer) {
        var account = offer.account();
        return account == null || account.isBlank() ? null : account.trim();
    }

    /**
     * Settles a closed contest: fixes its places and reserves its prizes in one transaction. The places go to the
     * companies that delivered and still exist, most items first; a cancelled contest pays no prizes.
     */
    private void settle(EventOffer offer, LocalDateTime now, Run run) {
        var ranking = places(offer);
        var cancelled = offer.state() == OfferState.CANCELLED;
        List<ContestPrize> rows = new ArrayList<>();
        var amounts = offer.prizes();
        for (var index = 0; index < amounts.size(); index++) {
            var amount = amounts.get(index);
            var winner = !cancelled && index < ranking.size() && amount > 0 ? ranking.get(index) : null;
            rows.add(new ContestPrize(
                    index + 1,
                    amount,
                    winner == null ? null : winner.company(),
                    null,
                    winner == null ? PrizeState.NONE : PrizeState.PENDING));
        }
        if (offers.settle(offer.id(), rows, now)) run.settled().add(offer.id());
    }

    /**
     * Pays the waiting prizes of a settled contest, each claimed before its money moves so it is paid at most once,
     * then gives back what its account paid in and was not spent, and announces the result, once. Nothing is given
     * back while a prize still waits or the prizes cannot be read, so a contest settled by a check that stopped halfway
     * is finished by a later one.
     */
    private void finish(EventOffer offer, LocalDateTime now) {
        var rows = offers.prizes(offer.id());
        if (rows.size() != offer.prizes().size()) return;
        for (var prize : rows) {
            var company = prize.company();
            if (prize.state() != PrizeState.PENDING || company == null) continue;
            if (!offers.claimPrize(offer.id(), prize.place())) continue;
            var paid = false;
            try {
                paid = prizes.pay(company, offer.id(), prize.amount());
            } catch (RuntimeException e) {
                logger.log(
                        Level.SEVERE,
                        "Could not pay the prize of place " + prize.place() + " of contest " + offer.id(),
                        e);
            }
            var noted = false;
            try {
                noted = offers.prizeOutcome(offer.id(), prize.place(), paid);
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Could not note the prize of place " + prize.place(), e);
            }
            if (!noted) {
                logger.severe("Could not note whether the prize of place " + prize.place() + " of contest " + offer.id()
                        + " was paid (" + paid + "); it is not paid again.");
            }
        }
        // A prize still waiting, or prizes that could not be read, keep the rest until a later check paid them.
        var after = offers.prizes(offer.id());
        if (after.size() != offer.prizes().size()
                || after.stream().anyMatch(prize -> prize.state() == PrizeState.PENDING)) {
            return;
        }
        var current = offers.offer(offer.id()).orElse(offer);
        var account = current.source() == MoneySource.SourceType.ACCOUNT ? accountOf(current) : null;
        var rest = account == null ? 0 : Math.max(0, current.held() - current.spent());
        if (!offers.refund(offer.id(), rest, now)) return;
        if (account != null && rest >= 0.005) funding.deposit(account, rest);
        if (current.started() == null) return;
        try {
            if (current.state() == OfferState.CANCELLED) {
                announcer.contestCancelled(current);
            } else {
                announcer.contestClosed(current, places(current), offers.prizes(offer.id()));
            }
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Could not announce the result of contest " + offer.id(), e);
        }
    }

    /**
     * @return the companies that delivered into the contest and still exist, in the order of their places
     */
    private List<ContestEntry> places(EventOffer offer) {
        return offers.ranking(offer.id()).stream()
                .filter(entry -> entry.delivered() > 0 && entry.name() != null)
                .toList();
    }
}
