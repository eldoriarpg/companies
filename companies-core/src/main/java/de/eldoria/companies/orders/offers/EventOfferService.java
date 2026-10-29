/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.offers;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.data.repository.AOfferData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.offer.ContestEntry;
import de.eldoria.companies.data.wrapper.offer.ContestPrize;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferKind;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.data.wrapper.offer.OpenChunk;
import de.eldoria.companies.orders.system.Purchases;
import de.eldoria.companies.orders.system.RuleItems;
import de.eldoria.companies.orders.system.SystemOrderService.Funding;
import de.eldoria.companies.orders.system.SystemOrderService.Placement;
import de.eldoria.companies.orders.system.SystemOrderService.Step;
import de.eldoria.companies.orders.system.SystemOrderService.Wanted;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Runs the goals and contests of the event offers. Only the primary node runs it, every {@value #CHECK_MINUTES}
 * minute and right after an admin changed an offer on this node.
 * <p>
 * Each check first settles the open chunks of every offer it looks after: a chunk whose order was delivered counts its
 * items for the offer and for whoever delivered it, a chunk whose order is gone without being delivered gives its amount
 * back, so it is placed again. Then the offer moves on: a scheduled goal starts at its start, a running goal is
 * {@link OfferState#REACHED reached} once its whole amount was delivered, which is announced, and
 * {@link OfferState#ENDED ends} when its end passes first.
 * <p>
 * A running goal keeps {@code openChunks} chunks waiting to be taken. Each chunk is a system order of {@code chunkSize}
 * items, the last one the rest that is neither delivered nor in an open chunk; it is paid like the orders of system
 * order rules, by {@link Purchases}, from the offer's account or minted, within what is left of the offer's budget. The
 * caps and budgets of the rules do not count chunks. When the budget cannot pay one more item while items are missing
 * and no chunk is open anymore, the goal closes as {@link OfferState#DEPLETED}; so it does when a listener of the order
 * creation raises the price of a chunk beyond the budget. A goal fully delivered while paused or before its start is
 * reached all the same. A goal that is paused or closed has its waiting chunks withdrawn, their money going back to the
 * account that paid them; chunks already taken may still be delivered until their own delivery time and count for the
 * offer. A chunk that is aborted or expires is open again like any order and is withdrawn when its offer no longer
 * runs.
 * <p>
 * Contests are started, closed and settled by {@link ContestJob}.
 */
public final class EventOfferService {
    /**
     * Minutes between two checks.
     */
    public static final int CHECK_MINUTES = 1;

    /**
     * What the chunks of an offer may spend beyond its budget, for the cents a chunk re-placed after a short delivery
     * may round to.
     */
    private static final double ROUNDING = 0.01;

    private final AOfferData offers;
    private final Placement placement;
    private final Funding funding;
    private final Purchases purchases;
    private final ContestJob contests;
    private final Announcer announcer;
    private final Clock clock;
    private final DoubleSupplier random;
    private final Logger logger;
    private final Map<Integer, Set<Integer>> refused = new ConcurrentHashMap<>();
    private volatile @Nullable ScheduledExecutorService executor;

    /**
     * @param offers    offer repository
     * @param orders    order repository, holding the decisions of the chunks
     * @param placement places and removes the chunks
     * @param funding   moves the money of accounts
     * @param announcer tells the server about reached goals
     * @param clock     clock giving the current time in UTC
     * @param random    a random number from 0 included to 1 excluded, picking a material of a criteria set
     * @param logger    reports chunks that could not be placed and failures
     */
    public EventOfferService(
            AOfferData offers,
            AOrderData orders,
            Placement placement,
            Funding funding,
            Announcer announcer,
            Clock clock,
            DoubleSupplier random,
            Logger logger) {
        this(offers, orders, placement, funding, (company, offer, amount) -> false, announcer, clock, random, logger);
    }

    /**
     * @param offers    offer repository
     * @param orders    order repository, holding the decisions of the chunks
     * @param placement places and removes the chunks
     * @param funding   moves the money of accounts
     * @param prizes    pays the prizes of contests
     * @param announcer tells the server about reached goals and closed contests
     * @param clock     clock giving the current time in UTC
     * @param random    a random number from 0 included to 1 excluded, picking a material of a criteria set
     * @param logger    reports chunks that could not be placed and failures
     */
    public EventOfferService(
            AOfferData offers,
            AOrderData orders,
            Placement placement,
            Funding funding,
            Prizes prizes,
            Announcer announcer,
            Clock clock,
            DoubleSupplier random,
            Logger logger) {
        this.offers = offers;
        this.placement = placement;
        this.funding = funding;
        this.announcer = announcer;
        this.clock = clock;
        this.random = random;
        this.logger = logger;
        purchases = new Purchases(orders, placement, funding, logger, "event offer");
        contests = new ContestJob(offers, funding, prizes, announcer, purchases, logger);
    }

    /**
     * Runs a check every {@value #CHECK_MINUTES} minute.
     *
     * @param executor executor running the checks
     */
    public void schedule(ScheduledExecutorService executor) {
        this.executor = executor;
        executor.scheduleWithFixedDelay(this::check, 1L, CHECK_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Runs a check soon, after an admin changed an offer. Does nothing on a node that does not run the checks.
     */
    public void checkSoon() {
        var running = executor;
        if (running == null) return;
        try {
            running.execute(this::check);
        } catch (RejectedExecutionException e) {
            logger.log(Level.FINE, "Not checking the event offers while the plugin stops.", e);
        }
    }

    private void check() {
        try {
            run();
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not look after the event offers", e);
        }
    }

    /**
     * Looks after every offer that runs or still has open chunks.
     *
     * @return what the check did
     */
    public synchronized Run run() {
        var now = LocalDateTime.now(clock);
        var run = new Run(
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new LinkedHashMap<>(),
                new ArrayList<>());
        for (var offer : offers.working()) {
            try {
                if (offer.kind() == OfferKind.CONTEST) contests.handle(offer, now, run);
                else handle(offer, now, run);
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Could not look after the event offer #" + offer.id(), e);
            }
        }
        return run;
    }

    private void handle(EventOffer offer, LocalDateTime now, Run run) {
        settle(offer.id(), now, run);
        var current = offers.offer(offer.id()).orElse(null);
        if (current == null) return;
        current = advance(current, now, run);
        var open = offers.openChunks(current.id());
        if (current.state() != OfferState.ACTIVE) {
            refused.remove(current.id());
            withdraw(open, now, run);
            return;
        }
        // Read after the chunks: a chunk settled in between then counts twice and places less, never more.
        var counted = offers.offer(current.id()).orElse(null);
        if (counted == null || counted.state() != OfferState.ACTIVE) return;
        place(counted, open, now, run);
    }

    /**
     * Settles the chunks whose orders were delivered or are gone.
     */
    private void settle(int offer, LocalDateTime now, Run run) {
        for (var chunk : offers.openChunks(offer)) {
            var state = chunk.state();
            if (state == null) {
                if (offers.chunkReturned(chunk.order(), now)) run.returned().add(chunk.order());
            } else if (state.stateId() >= OrderState.DELIVERED.stateId()) {
                if (offers.chunkDelivered(chunk.order(), now)) run.delivered().add(chunk.order());
            }
        }
    }

    /**
     * Starts, reaches or ends an offer when it is time.
     *
     * @return the offer in its new state
     */
    private EventOffer advance(EventOffer offer, LocalDateTime now, Run run) {
        var current = offer;
        while (true) {
            var next = next(current, now);
            if (next == null || !offers.state(current.id(), current.state(), next, now)) return current;
            run.changed().put(current.id(), next);
            current = offers.offer(current.id()).orElse(current);
            if (next == OfferState.REACHED) announce(current);
        }
    }

    private static @Nullable OfferState next(EventOffer offer, LocalDateTime now) {
        var ended = !now.isBefore(offer.ends());
        return switch (offer.state()) {
            case SCHEDULED -> {
                if (offer.reached()) yield OfferState.REACHED;
                if (ended) yield OfferState.ENDED;
                yield now.isBefore(offer.starts()) ? null : OfferState.ACTIVE;
            }
            case ACTIVE, PAUSED -> {
                if (offer.reached()) yield OfferState.REACHED;
                if (ended) yield OfferState.ENDED;
                yield null;
            }
            default -> null;
        };
    }

    private void announce(EventOffer offer) {
        try {
            announcer.reached(offer);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Could not announce the reached event offer #" + offer.id(), e);
        }
    }

    /**
     * Withdraws the chunks nobody took yet and gives their money back to the account that paid them.
     */
    private void withdraw(List<OpenChunk> open, LocalDateTime now, Run run) {
        for (var chunk : open) {
            if (!chunk.unclaimed()) continue;
            boolean removed;
            try {
                removed = placement.remove(chunk.order());
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Could not withdraw the chunk " + chunk.order() + " of an event offer", e);
                continue;
            }
            if (!removed) continue;
            var account = chunk.account();
            if (account != null) funding.deposit(account, chunk.paid());
            offers.chunkReturned(chunk.order(), now);
            run.withdrawn().add(chunk.order());
        }
    }

    /**
     * Places chunks until {@code openChunks} wait to be taken, nothing is left to place, or the money stops it.
     */
    private void place(EventOffer offer, List<OpenChunk> open, LocalDateTime now, Run run) {
        var label = "#" + offer.id();
        var waiting = (int) open.stream().filter(OpenChunk::unclaimed).count();
        var inWork = open.size();
        long placed = offer.delivered();
        for (var chunk : open) placed += chunk.amount();
        var missing = offer.amount() - placed;
        var budget = offer.budget() - offer.spent() + ROUNDING;
        var chunks = open.stream().map(OpenChunk::order).collect(Collectors.toCollection(HashSet::new));
        if (missing > 0 && waiting < offer.openChunks() && chunks.equals(refused.get(offer.id()))) return;
        refused.remove(offer.id());
        while (waiting < offer.openChunks() && missing > 0) {
            var amount = EventOffer.affordable(offer.unitPrice(), (int) Math.min(offer.chunkSize(), missing), budget);
            if (amount <= 0) {
                outOfMoney(offer, inWork, now, run, label);
                return;
            }
            var bought = buy(offer, amount, budget, now, label);
            if (bought.step() == Step.BUDGET) {
                if (inWork > 0) refused.put(offer.id(), Set.copyOf(chunks));
                outOfMoney(offer, inWork, now, run, label);
                return;
            }
            if (bought.step() != Step.PLACED || bought.order().isEmpty()) return;
            var order = bought.order().getAsInt();
            chunks.add(order);
            run.placed().add(order);
            waiting++;
            inWork++;
            missing -= amount;
            budget -= bought.paid();
        }
    }

    /**
     * The budget cannot pay the next chunk, at its price or at the price a listener of the order creation raised it
     * to: with no chunk open the goal is depleted, otherwise it waits for its chunks to settle. A chunk refused for its
     * raised price is not tried again while the same chunks are open, so it is not placed and removed every check.
     */
    private void outOfMoney(EventOffer offer, int inWork, LocalDateTime now, Run run, String label) {
        if (inWork == 0 && offers.state(offer.id(), OfferState.ACTIVE, OfferState.DEPLETED, now)) {
            run.changed().put(offer.id(), OfferState.DEPLETED);
        } else {
            purchases.note(label, Step.BUDGET, "");
        }
    }

    /**
     * Places one chunk and notes it for the offer.
     *
     * @return what was bought, with the order only when it was placed and noted
     */
    private Purchases.Bought buy(EventOffer offer, int amount, double budget, LocalDateTime now, String label) {
        var resolved = RuleItems.resolve(offer.line().entry(), 0, placement::knows, Set::of, random);
        if (!(resolved instanceof RuleItems.Chosen chosen)) {
            return new Purchases.Bought(
                    purchases.note(label, Step.UNKNOWN_ITEM, offer.line().label()), OptionalInt.empty(), 0);
        }
        var wanted =
                new Wanted(EventOffer.RULE, offer.name(), chosen.label(), amount, offer.unitPrice(), chosen.line());
        var account = offer.account() == null ? "" : offer.account();
        var bought = purchases.buy(
                label,
                wanted,
                offer.source(),
                account,
                budget,
                null,
                now,
                OfferRecord.KIND,
                money -> new OfferRecord(
                        offer.id(),
                        offer.name(),
                        chosen.label(),
                        amount,
                        offer.unitPrice(),
                        money.paid(),
                        money.source(),
                        money.account(),
                        money.budgetLeft()));
        if (bought.step() != Step.PLACED || bought.order().isEmpty()) return bought;
        var order = bought.order().getAsInt();
        var paidFrom = offer.source() == MoneySource.SourceType.ACCOUNT ? account.trim() : null;
        boolean stored;
        try {
            stored = offers.addChunk(offer.id(), order, amount, bought.paid(), paidFrom, now);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not note the chunk " + order + " of the event offer #" + offer.id(), e);
            stored = false;
        }
        if (stored) return bought;
        undo(order, paidFrom, bought.paid());
        return new Purchases.Bought(
                purchases.note(label, Step.FAILED, "chunk not stored, order removed"), OptionalInt.empty(), 0);
    }

    /**
     * Removes a chunk that could not be noted for its offer, so no chunk exists that the offer does not count.
     */
    private void undo(int order, @Nullable String account, double paid) {
        boolean removed;
        try {
            removed = placement.remove(order);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not remove the chunk " + order + " again", e);
            removed = false;
        }
        if (removed) {
            if (account != null) funding.deposit(account, paid);
        } else {
            logger.severe("The chunk " + order + " could not be noted for its offer nor removed; it stays open as a"
                    + " system order and does not count for the offer.");
        }
    }

    /**
     * Tells the server about the offers.
     */
    @FunctionalInterface
    public interface Announcer {
        /**
         * Announces that a goal was reached.
         *
         * @param offer the goal
         */
        void reached(EventOffer offer);

        /**
         * Announces the result of a closed contest.
         *
         * @param offer   the contest, settled
         * @param ranking the companies that delivered and still exist, in the order of their places
         * @param prizes  the prizes of its places as they were paid
         */
        default void contestClosed(EventOffer offer, List<ContestEntry> ranking, List<ContestPrize> prizes) {}

        /**
         * Announces that a running contest was cancelled, without prizes.
         *
         * @param offer the contest
         */
        default void contestCancelled(EventOffer offer) {}
    }

    /**
     * Pays the prizes of contests.
     */
    @FunctionalInterface
    public interface Prizes {
        /**
         * Pays a prize to a company.
         *
         * @param company id of the company
         * @param offer   id of the contest
         * @param amount  the prize
         * @return true when the money reached the company
         */
        boolean pay(int company, int offer, double amount);
    }

    /**
     * What a check did.
     *
     * @param placed    the chunks placed
     * @param withdrawn the chunks withdrawn, since their offer was paused or closed
     * @param delivered the chunks settled as delivered
     * @param returned  the chunks settled as gone without being delivered
     * @param changed   the offers that changed their state, with the last state they reached
     * @param settled   the contests settled: their places fixed and their prizes reserved
     */
    public record Run(
            List<Integer> placed,
            List<Integer> withdrawn,
            List<Integer> delivered,
            List<Integer> returned,
            Map<Integer, OfferState> changed,
            List<Integer> settled) {}
}
