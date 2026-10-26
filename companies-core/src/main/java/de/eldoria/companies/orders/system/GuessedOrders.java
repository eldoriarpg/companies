/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.systemorders.GuessedSettings;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleSupplier;
import java.util.stream.Collectors;

/**
 * Keeps the guessed orders of {@code system-orders.yml} running, one check at a time:
 * <ol>
 *     <li>An open guessed order a player now competes with is withdrawn: in {@code SKIP} mode any open player order
 *     of the material, in {@code UNDERBID} mode one that the guessed order no longer pays clearly less than.</li>
 *     <li>An open guessed order nobody took within {@code repriceAfterHours} of its placement or last extension is
 *     removed. While it has reprices left and its material is still a candidate, a copy at the next price step
 *     replaces it; otherwise its material rests for {@code restHours}.</li>
 *     <li>New guessed orders, replacements first, fill up to the target of {@link GuessedChoice#target}, in the order
 *     of {@link GuessedChoice#rank}, one per material, skipping resting materials and, in {@code SKIP} mode, those
 *     players have open orders for.</li>
 * </ol>
 * While guessed orders or system orders are disabled, open ones are removed when they would be repriced, and none are placed. Removed
 * orders give their money back to the account that paid them.
 */
final class GuessedOrders {
    private final AOrderData orders;
    private final Placement placement;
    private final Purchases purchases;
    private final DoubleSupplier random;

    /**
     * @param orders    order repository
     * @param placement places and removes the orders
     * @param purchases pays for orders and gives money back
     * @param random    a random number from 0 included to 1 excluded
     */
    GuessedOrders(AOrderData orders, Placement placement, Purchases purchases, DoubleSupplier random) {
        this.orders = orders;
        this.placement = placement;
        this.purchases = purchases;
        this.random = random;
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
                }
            }
            return;
        }
        var demand = demand();
        var ranking = GuessedChoice.rank(
                settings,
                orders.deliveredVolume(
                        now.minusDays(settings.historyDays()), now.minusDays(settings.recentDays()), now));
        var ranks = new HashMap<String, Integer>();
        for (var index = 0; index < ranking.size(); index++)
            ranks.put(ranking.get(index).material(), index);
        var replacements = new ArrayList<Replacement>();
        var stillOpen = 0;
        for (var order : open) {
            var record = order.record();
            var material = record == null ? null : record.material();
            if (record != null && competes(settings, record, demand.get(material))) {
                if (purchases.remove(order.decision()))
                    removed.add(order.decision().order());
                continue;
            }
            if (!due.contains(order.decision().order())) {
                stillOpen++;
                continue;
            }
            if (!purchases.remove(order.decision())) continue;
            removed.add(order.decision().order());
            if (material == null) continue;
            if (record.step() < settings.maxReprices() && ranks.containsKey(material)) {
                replacements.add(new Replacement(
                        material, record.step() + 1, order.decision().order()));
            } else {
                orders.restMaterial(material, now.plusHours(settings.restHours()));
            }
        }
        var active = guessed(OrderState.UNCLAIMED, OrderState.CLAIMED).stream()
                .map(Open::record)
                .filter(Objects::nonNull)
                .map(GuessedRecord::material)
                .collect(Collectors.toCollection(HashSet::new));
        var openPlayer = orders.openPlayerOrders();
        var target = GuessedChoice.target(settings, openPlayer);
        var round = new Placing(current, now, dayStart, demand, ranking, openPlayer, target, results);
        var slots = target - stillOpen;
        for (var replacement : replacements) {
            if (slots <= 0) break;
            if (!active.add(replacement.material())) continue;
            var candidate = ranking.get(ranks.get(replacement.material()));
            if (place(
                    round,
                    candidate,
                    ranks.get(candidate.material()),
                    replacement.step(),
                    replacement.replaces(),
                    stillOpen)) {
                slots--;
                stillOpen++;
            }
        }
        var resting = orders.restingMaterials(now);
        for (var index = 0; index < ranking.size() && slots > 0; index++) {
            var candidate = ranking.get(index);
            if (resting.contains(candidate.material()) || active.contains(candidate.material())) continue;
            if (settings.competition() == GuessedSettings.Competition.SKIP && demand.containsKey(candidate.material()))
                continue;
            active.add(candidate.material());
            if (place(round, candidate, index, 0, null, stillOpen)) {
                slots--;
                stillOpen++;
            }
        }
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
            if (!GuessedSettings.RULE.equals(decision.rule())) continue;
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
     */
    private record Placing(
            SystemOrderSettings current,
            LocalDateTime now,
            LocalDateTime dayStart,
            Map<String, PlayerDemand> demand,
            List<Candidate> ranking,
            int openPlayer,
            int target,
            List<Result> results) {}

    private boolean place(
            Placing placing, Candidate candidate, int index, int step, @Nullable Integer replaces, int openGuessed) {
        var settings = placing.current().guessed();
        var material = candidate.material();
        var label = GuessedSettings.RULE + " " + material;
        Step result;
        if (!placement.knows(material)) {
            result = purchases.note(label, Step.UNKNOWN_ITEM, material);
        } else {
            var average = placement.average(material);
            var demand = placing.demand().get(material);
            var price = average.isEmpty()
                    ? Optional.<GuessedChoice.Price>empty()
                    : GuessedChoice.price(settings, average.getAsDouble(), step, demand, random);
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
                                        money.mintedLeft()))
                        .step();
            }
        }
        placing.results().add(new Result(label, result));
        return result == Step.PLACED;
    }

    /**
     * An open guessed order with its decision record.
     *
     * @param decision the decision
     * @param record   its record, null when it cannot be read
     */
    private record Open(SystemDecision decision, @Nullable GuessedRecord record) {}

    /**
     * A copy at a higher price that replaces a removed guessed order.
     *
     * @param material the material
     * @param step     the next price step
     * @param replaces the order it replaces
     */
    private record Replacement(String material, int step, int replaces) {}
}
