/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.systemorders.GuessedSettings;
import de.eldoria.companies.configuration.elements.systemorders.LearningSettings;
import de.eldoria.companies.configuration.elements.systemorders.SystemOrderSettings;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.PlayerDemand;
import de.eldoria.companies.data.wrapper.order.SystemDecision;
import de.eldoria.companies.orders.system.GuessedChoice.Candidate;
import de.eldoria.companies.orders.system.SystemOrderService.Placement;
import de.eldoria.companies.orders.system.SystemOrderService.Result;
import de.eldoria.companies.orders.system.SystemOrderService.Step;
import de.eldoria.companies.orders.system.SystemOrderService.Wanted;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Keeps the guessed orders of {@code system-orders.yml} running, one check at a time:
 * <ol>
 *     <li>An open guessed order a player now competes with is withdrawn: in {@code SKIP} mode any open player order
 *     of the material, in {@code UNDERBID} mode one that the guessed order no longer pays clearly less than.</li>
 *     <li>An open guessed order nobody took within {@code repriceAfterHours} of its placement or last extension is
 *     removed. While it has reprices left, its material is still a candidate and an underbidding cap did not already
 *     hold its price down, a copy at the next price step replaces it; otherwise its material rests for
 *     {@code restHours}. A replacement that finds no free slot, or cannot be placed for a reason other than money,
 *     lets its material rest too.</li>
 *     <li>New guessed orders, replacements first, fill up to the target of {@link GuessedChoice#target}, one per
 *     material. While learning is on, each is drawn at random weighted as {@link GuessedLearning} says and priced
 *     from the factor it learned; otherwise they follow the order of {@link GuessedChoice#rank}. Either way they skip
 *     resting materials and, in {@code SKIP} mode, those players have open orders for. A placement that fails is
 *     logged and the next candidate is tried, also when money stopped it, since a cheaper one may still fit; a
 *     missing account, or money stopping {@value #MONEY_FAILURES} placements, ends the check's placing. A
 *     replacement money stopped is placed later as a new order.</li>
 * </ol>
 * The allow and deny lists are resolved to material names first; entries naming no item are logged. While guessed
 * orders or system orders are disabled, open ones are removed when they would be repriced, and none are placed.
 * Removed orders give their money back to the account that paid them. How a removed order ended is noted for
 * learning: {@code REPRICED} when a copy at a higher price replaced it, {@code EXPIRED} when nobody took the last
 * copy of a chain, {@code WITHDRAWN} when it made way for players or
 * guessed orders were switched off.
 */
final class GuessedOrders {
    /**
     * Placements money may stop in one check before the check stops placing.
     */
    static final int MONEY_FAILURES = 3;

    private final AOrderData orders;
    private final Placement placement;
    private final Purchases purchases;
    private final DoubleSupplier random;
    private final Logger logger;

    /**
     * @param orders    order repository
     * @param placement places and removes the orders
     * @param purchases pays for orders and gives money back
     * @param random    a random number from 0 included to 1 excluded
     * @param logger    reports placements that failed
     */
    GuessedOrders(AOrderData orders, Placement placement, Purchases purchases, DoubleSupplier random, Logger logger) {
        this.orders = orders;
        this.placement = placement;
        this.purchases = purchases;
        this.random = random;
        this.logger = logger;
    }

    /**
     * Runs one check.
     *
     * @param current  the current settings
     * @param placing  whether guessed orders are placed: system orders and guessed orders are both enabled
     * @param now      the current time
     * @param dayStart start of the current day of the server's calendar
     * @param removed  collects the orders removed
     * @param results  collects what each placement did
     */
    void run(
            SystemOrderSettings current,
            boolean placing,
            LocalDateTime now,
            LocalDateTime dayStart,
            List<Integer> removed,
            List<Result> results) {
        var settings = current.guessed();
        var open = guessed(OrderState.UNCLAIMED);
        var due =
                orders
                        .unclaimedSystemOrders(GuessedSettings.RULE, now.minusHours(settings.repriceAfterHours()))
                        .stream()
                        .map(SystemDecision::order)
                        .collect(Collectors.toSet());
        if (!placing) {
            for (var order : open) {
                if (due.contains(order.decision().order()) && purchases.remove(order.decision())) {
                    removed.add(order.decision().order());
                    ended(order, GuessedLearning.WITHDRAWN, now);
                }
            }
            return;
        }
        var demand = demand();
        var ranking = GuessedChoice.rank(
                settings,
                orders.deliveredVolume(
                        now.minusDays(settings.historyDays()), now.minusDays(settings.recentDays()), now),
                resolved(settings.allow()),
                resolved(settings.deny()));
        var ranks = new HashMap<String, Integer>();
        for (var index = 0; index < ranking.size(); index++) {
            ranks.put(ranking.get(index).material(), index);
        }
        var pending = new ArrayDeque<Replacement>();
        try {
            var stillOpen = 0;
            for (var order : open) {
                var record = order.record();
                var material = record == null ? null : record.material();
                if (record != null && competes(settings, record, demand.get(material))) {
                    if (purchases.remove(order.decision())) {
                        removed.add(order.decision().order());
                        ended(order, GuessedLearning.WITHDRAWN, now);
                    }
                    continue;
                }
                if (!due.contains(order.decision().order())) {
                    stillOpen++;
                    continue;
                }
                if (!purchases.remove(order.decision())) continue;
                removed.add(order.decision().order());
                if (material == null) {
                    ended(order, GuessedLearning.EXPIRED, now);
                    continue;
                }
                if (record.step() < settings.maxReprices() && ranks.containsKey(material) && !capped(record)) {
                    pending.add(new Replacement(material, record.step() + 1, order));
                } else {
                    ended(order, GuessedLearning.EXPIRED, now);
                    rest(settings, material, now);
                }
            }
            var active = guessed(OrderState.UNCLAIMED, OrderState.CLAIMED).stream()
                    .map(Open::record)
                    .filter(Objects::nonNull)
                    .map(GuessedRecord::material)
                    .collect(Collectors.toCollection(HashSet::new));
            var openPlayer = orders.openPlayerOrders();
            var target = GuessedChoice.target(settings, openPlayer);
            var slots = target - stillOpen;
            var learning = slots > 0 ? learning(settings, now) : null;
            var round = new Placing(current, now, dayStart, demand, ranking, openPlayer, target, results, learning);
            var moneyFailures = 0;
            while (!pending.isEmpty()) {
                var replacement = pending.poll();
                var material = replacement.material();
                if (slots <= 0 || active.contains(material)) {
                    ended(replacement.replaced(), GuessedLearning.EXPIRED, now);
                    rest(settings, material, now);
                    continue;
                }
                active.add(material);
                var index = ranks.get(material);
                var replaces = replacement.replaced().decision().order();
                var step = place(round, ranking.get(index), index, replacement.step(), replaces, stillOpen, null);
                if (step == Step.PLACED) {
                    ended(replacement.replaced(), GuessedLearning.REPRICED, now);
                    slots--;
                    stillOpen++;
                    continue;
                }
                ended(replacement.replaced(), GuessedLearning.EXPIRED, now);
                if (step == Step.NO_ACCOUNT || money(step) && ++moneyFailures >= MONEY_FAILURES) return;
                if (!money(step)) rest(settings, material, now);
            }
            var resting = orders.restingMaterials(now);
            var eligible = new ArrayList<Candidate>();
            for (var candidate : ranking) {
                if (resting.contains(candidate.material()) || active.contains(candidate.material())) continue;
                if (settings.competition() == GuessedSettings.Competition.SKIP
                        && demand.containsKey(candidate.material())) continue;
                eligible.add(candidate);
            }
            while (slots > 0 && !eligible.isEmpty()) {
                var pick = 0;
                Double weight = null;
                if (learning != null) {
                    var weights = eligible.stream()
                            .map(candidate -> weight(settings, learning, candidate))
                            .toList();
                    pick = Math.max(0, GuessedLearning.draw(weights, random));
                    weight = weights.get(pick);
                }
                var candidate = eligible.remove(pick);
                active.add(candidate.material());
                var index = ranks.get(candidate.material());
                var step = place(round, candidate, index, 0, null, stillOpen, weight);
                if (step == Step.PLACED) {
                    slots--;
                    stillOpen++;
                } else if (step == Step.NO_ACCOUNT || money(step) && ++moneyFailures >= MONEY_FAILURES) {
                    return;
                }
            }
        } finally {
            for (var replacement : pending) ended(replacement.replaced(), GuessedLearning.EXPIRED, now);
        }
    }

    /**
     * What learning knows of the guessed orders of the last {@code days}, per material; null while learning is off or
     * when it cannot be read, so the check goes on by rank at the configured factor.
     *
     * @param settings the guessed order settings
     * @param now      the current time
     * @return the statistics per material, null without learning
     */
    private @Nullable Map<String, GuessedLearning.Stats> learning(GuessedSettings settings, LocalDateTime now) {
        if (!settings.learning().enabled()) return null;
        try {
            return GuessedLearning.stats(
                    orders, settings, now.minusDays(settings.learning().days()));
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Could not read what guessed orders learned; placing by rank without it", e);
            return null;
        }
    }

    private static double weight(
            GuessedSettings settings, Map<String, GuessedLearning.Stats> learning, Candidate candidate) {
        var stats = learning.get(candidate.material());
        var floor = settings.learning().weightFloor();
        var takeRate = stats == null ? Math.max(floor, 0.5) : stats.takeRate(floor);
        return takeRate / (1 + candidate.score());
    }

    /**
     * Notes how an order this check removed ended. The order was unclaimed, so a {@code TAKEN} noted before belongs
     * to a take given up again and is replaced.
     */
    private void ended(Open order, String outcome, LocalDateTime now) {
        try {
            orders.systemOutcome(order.decision().order(), outcome, now, GuessedLearning.TAKEN);
        } catch (RuntimeException e) {
            logger.log(
                    Level.WARNING,
                    "Could not note how the system order " + order.decision().order() + " ended",
                    e);
        }
    }

    /**
     * Whether an underbidding order already paid all its cap allowed, so a reprice could not pay more.
     *
     * @param record the order's decision record
     * @return true when its price was the cap
     */
    static boolean capped(GuessedRecord record) {
        var cap = record.cap();
        return cap != null && record.unit() >= cap - 0.000_001;
    }

    /**
     * Whether money kept an order from being placed, which says nothing about its material.
     *
     * @param step what a placement did
     * @return true for the budget, the minted cap and an account that cannot pay
     */
    private static boolean money(Step step) {
        return switch (step) {
            case BUDGET, MINTED_CAP, CANNOT_PAY -> true;
            default -> false;
        };
    }

    private void rest(GuessedSettings settings, String material, LocalDateTime now) {
        orders.restMaterial(material, now.plusHours(settings.restHours()));
    }

    /**
     * Resolves materials as written in the settings to material names, logging those that name no item.
     *
     * @param written the materials as written
     * @return the material names
     */
    private Set<String> resolved(Set<String> written) {
        var names = new HashSet<String>();
        for (var material : written) {
            var name = placement.canonical(material);
            if (name == null) purchases.note(GuessedSettings.RULE + " " + material, Step.UNKNOWN_ITEM, material);
            else names.add(name);
        }
        return names;
    }

    /**
     * Whether players now compete with an open guessed order.
     *
     * @param settings the guessed order settings
     * @param record   the order's decision record
     * @param demand   the open player demand of its material, null when there is none
     * @return true when it is withdrawn
     */
    static boolean competes(GuessedSettings settings, GuessedRecord record, @Nullable PlayerDemand demand) {
        if (demand == null) return false;
        if (settings.competition() == GuessedSettings.Competition.SKIP) return true;
        var reference = Math.min(record.average(), demand.lowestPrice());
        return record.unit() > GuessedChoice.cap(reference, settings.margin()) + 0.000_001;
    }

    /**
     * @param material a material name in upper case
     * @return the name a guessed order of it carries, such as {@code Iron Ingot}
     */
    static String name(String material) {
        return Arrays.stream(material.toLowerCase(Locale.ROOT).split("_"))
                .filter(part -> !part.isEmpty())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    private Map<String, PlayerDemand> demand() {
        var demand = new HashMap<String, PlayerDemand>();
        orders.openPlayerDemand()
                .forEach((material, entry) -> demand.merge(
                        material.toUpperCase(Locale.ROOT),
                        entry,
                        (first, second) -> new PlayerDemand(
                                first.material(),
                                first.orders() + second.orders(),
                                first.items() + second.items(),
                                Math.min(first.lowestPrice(), second.lowestPrice()))));
        return demand;
    }

    private List<Open> guessed(OrderState state) {
        return guessed(state, state);
    }

    private List<Open> guessed(OrderState min, OrderState max) {
        var found = new ArrayList<Open>();
        for (var decision : orders.systemDecisions(min, max)) {
            if (!GuessedSettings.RULE.equals(decision.rule()) || !GuessedRecord.KIND.equals(decision.kind())) continue;
            found.add(new Open(decision, purchases.read(decision, GuessedRecord.class)));
        }
        return found;
    }

    /**
     * Places guessed orders within one check.
     *
     * @param current    the current settings
     * @param now        the current time
     * @param dayStart   start of the current day
     * @param demand     open player demand per material
     * @param ranking    the ranked candidates
     * @param openPlayer open player orders
     * @param target     the target of open guessed orders
     * @param results    collects what each placement did
     * @param learning   what learning knows per material, null without learning
     */
    private record Placing(
            SystemOrderSettings current,
            LocalDateTime now,
            LocalDateTime dayStart,
            Map<String, PlayerDemand> demand,
            List<Candidate> ranking,
            int openPlayer,
            int target,
            List<Result> results,
            @Nullable Map<String, GuessedLearning.Stats> learning) {}

    private Step place(
            Placing placing,
            Candidate candidate,
            int index,
            int step,
            @Nullable Integer replaces,
            int openGuessed,
            @Nullable Double weight) {
        var label = GuessedSettings.RULE + " " + candidate.material();
        Step result;
        try {
            result = placeNow(placing, candidate, index, step, replaces, openGuessed, weight, label);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not place the guessed system order of " + candidate.material(), e);
            result = Step.FAILED;
        }
        placing.results().add(new Result(label, result));
        return result;
    }

    private Step placeNow(
            Placing placing,
            Candidate candidate,
            int index,
            int step,
            @Nullable Integer replaces,
            int openGuessed,
            @Nullable Double weight,
            String label) {
        var settings = placing.current().guessed();
        var material = candidate.material();
        var known = placing.learning();
        var stats = known == null ? null : known.get(material);
        var learned = settings.learning();
        var base = stats == null ? settings.factor() : stats.factor();
        Step result;
        if (!placement.knows(material)) {
            result = purchases.note(label, Step.UNKNOWN_ITEM, material);
        } else {
            var average = placement.average(material);
            var demand = placing.demand().get(material);
            var price = average.isEmpty()
                    ? Optional.<GuessedChoice.Price>empty()
                    : GuessedChoice.price(settings, base, average.getAsDouble(), step, demand, random);
            if (price.isEmpty()) {
                result = purchases.note(label, Step.NO_PRICE, "");
            } else {
                var priced = price.get();
                var amount = GuessedChoice.amount(settings, candidate.typical(), placement.stackSize(material));
                var wanted = new Wanted(GuessedSettings.RULE, name(material), material, amount, priced.unit());
                result = purchases
                        .buy(
                                label,
                                wanted,
                                settings.source(),
                                placing.current().mintedDailyCap(),
                                placing.now(),
                                placing.dayStart(),
                                GuessedRecord.KIND,
                                money -> new GuessedRecord(
                                        material,
                                        amount,
                                        candidate.typical(),
                                        settings.sizeShare(),
                                        candidate.orders(),
                                        candidate.longRate(),
                                        candidate.recentRate(),
                                        candidate.score(),
                                        index + 1,
                                        placing.ranking().size(),
                                        settings.competition(),
                                        demand == null ? 0 : demand.items(),
                                        priced.lowest(),
                                        priced.cap(),
                                        priced.average(),
                                        priced.factor(),
                                        priced.step(),
                                        replaces,
                                        priced.jitter(),
                                        priced.bounded(),
                                        priced.unit(),
                                        money.paid(),
                                        placing.openPlayer(),
                                        openGuessed,
                                        placing.target(),
                                        money.source(),
                                        money.account(),
                                        money.budgetLeft(),
                                        money.mintedLeft(),
                                        known == null
                                                ? null
                                                : learning(base, stats, weight, paidFactor(priced), learned)))
                        .step();
            }
        }
        return result;
    }

    private static double paidFactor(GuessedChoice.Price priced) {
        return priced.average() > 0 ? priced.unit() / priced.average() : priced.factor();
    }

    private static GuessedRecord.Learning learning(
            double base,
            GuessedLearning.@Nullable Stats stats,
            @Nullable Double weight,
            double factor,
            LearningSettings settings) {
        if (stats == null) {
            return new GuessedRecord.Learning(
                    base, 0, weight, Math.max(settings.weightFloor(), 0.5), 0, 0, 0, null, 0, 0);
        }
        var bucket = stats.bucket(factor);
        return new GuessedRecord.Learning(
                base,
                stats.offset(),
                weight,
                stats.takeRate(settings.weightFloor()),
                stats.posted(),
                stats.taken(),
                stats.expired(),
                stats.minutesToTake(),
                bucket[0],
                bucket[1]);
    }

    /**
     * An open guessed order with its decision record.
     *
     * @param decision the decision
     * @param record   its record, null when it cannot be read
     */
    private record Open(SystemDecision decision, @Nullable GuessedRecord record) {}

    /**
     * A copy at a higher price that replaces a removed guessed order. The removed order is noted {@code REPRICED} once
     * the copy is placed, {@code EXPIRED} otherwise, also when the check stops or fails before the copy is tried.
     *
     * @param material the material
     * @param step     the next price step
     * @param replaced the order it replaces
     */
    private record Replacement(String material, int step, Open replaced) {}
}
