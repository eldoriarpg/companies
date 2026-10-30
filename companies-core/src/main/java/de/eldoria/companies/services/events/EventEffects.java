/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import de.eldoria.companies.data.wrapper.event.EventKind;
import de.eldoria.companies.data.wrapper.event.ServerEvent;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ToDoubleFunction;

/**
 * How the effects of several server events combine on one order: reductions add, multipliers multiply. The tax never
 * drops below 0, a fee never below 0, and the multipliers together never pass the cap of {@code events.yml}.
 * <p>
 * Every function takes the events that apply to the order, as {@link ActiveEvents#matching} returns them, and uses
 * only those of its kind. Only {@link #extra} looks at the material scope again, since an item scoped multiplier pays
 * on the matching lines of an order only. Where the order of events matters, they are taken in the order they started,
 * by {@link ServerEvent#starts()} and then by id.
 */
public final class EventEffects {
    private static final Comparator<ServerEvent> STARTED =
            Comparator.comparing(ServerEvent::starts).thenComparingInt(ServerEvent::id);

    private EventEffects() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * The tax on a payout: the base tax less every tax reduction, in percentage points, never below 0.
     *
     * @param basePercent the tax without events, in percent
     * @param events      the events that apply to the order
     * @return the tax in percent
     */
    public static double taxPercent(double basePercent, Collection<ServerEvent> events) {
        return Math.max(0, basePercent - sum(events, EventKind.TAX_REDUCTION));
    }

    /**
     * The fee of an order or raise: the base fee less the fee reductions, which add up in percent, never below 0.
     *
     * @param baseFee the fee without events
     * @param events  the events that apply to the order
     * @return the fee
     */
    public static double fee(double baseFee, Collection<ServerEvent> events) {
        var reduction = Math.min(ServerEvent.MAX_REDUCTION, sum(events, EventKind.FEE_REDUCTION));
        return Math.max(0, baseFee * (1 - reduction / ServerEvent.MAX_REDUCTION));
    }

    /**
     * The factor on a payout: the product of every multiplier, at most the cap.
     *
     * @param events the events that apply to the order
     * @param max    the cap, {@code events.maxMultiplier}
     * @return the factor, at least 1
     */
    public static double multiplier(Collection<ServerEvent> events, double max) {
        var product = events.stream()
                .filter(event -> event.kind() == EventKind.REWARD_MULTIPLIER)
                .mapToDouble(ServerEvent::value)
                .reduce(1, (left, right) -> left * right);
        return Math.max(1, Math.min(Math.max(1, max), product));
    }

    /**
     * What the multipliers pay on top of a gross payout, and which event pays which part, when every event can pay its
     * whole part.
     *
     * @param lines  the gross payout per line of the order
     * @param events the events that apply to the order
     * @param max    the cap, {@code events.maxMultiplier}
     * @return the extra in all and per event
     * @see #extra(List, Collection, double, ToDoubleFunction)
     */
    public static Extra extra(List<Line> lines, Collection<ServerEvent> events, double max) {
        return extra(lines, events, max, event -> Double.POSITIVE_INFINITY);
    }

    /**
     * What the multipliers pay on top of a gross payout, and which event pays which part, within what each event can
     * still pay.
     * <p>
     * The events are walked in the order they started, by {@link ServerEvent#starts()} and then by id. Each line keeps
     * the factor reached so far, starting at 1. An event raises the factor of every line its material scope covers to
     * {@code factor × value}, at most the cap, and its part is {@code gross × (raised − factor)} summed over those
     * lines, so an event that starts once the cap is reached pays nothing for that line. When the event can pay less
     * than its part, it pays what it can and never more, also after rounding, spread over its lines in proportion to
     * their parts, and each of those lines continues from the factor the event really paid,
     * {@code factor + paid / gross}; the events that started later multiply on that factor, so they never pay the
     * compounding of money that was not paid.
     *
     * @param lines  the gross payout per line of the order
     * @param events the events that apply to the order
     * @param max    the cap, {@code events.maxMultiplier}
     * @param limit  what an event can still pay at most, such as the rest of its budget or its account's balance;
     *               {@link Double#POSITIVE_INFINITY} for no limit
     * @return the extra in all, per event, and the events that could not pay their whole part
     */
    public static Extra extra(
            List<Line> lines, Collection<ServerEvent> events, double max, ToDoubleFunction<ServerEvent> limit) {
        return extra(lines, events, max, limit, (event, paid) -> {});
    }

    /**
     * What the multipliers pay on top of a gross payout within what each event can still pay, telling after each event
     * what it pays, so limits that several events share, such as the balance of one account, shrink before the next
     * event asks for its limit.
     *
     * @param lines  the gross payout per line of the order
     * @param events the events that apply to the order
     * @param max    the cap, {@code events.maxMultiplier}
     * @param limit  what an event can still pay at most; {@link Double#POSITIVE_INFINITY} for no limit
     * @param paid   hears what each event pays, in the order the events started
     * @return the extra in all, per event, and the events that could not pay their whole part
     * @see #extra(List, Collection, double, ToDoubleFunction)
     */
    public static Extra extra(
            List<Line> lines,
            Collection<ServerEvent> events,
            double max,
            ToDoubleFunction<ServerEvent> limit,
            ObjDoubleConsumer<ServerEvent> paid) {
        var cap = Math.max(1, max);
        var multipliers = events.stream()
                .filter(event -> event.kind() == EventKind.REWARD_MULTIPLIER)
                .sorted(STARTED)
                .toList();
        var paying = lines.stream().filter(line -> line.gross() > 0).toList();
        var factors = new double[paying.size()];
        Arrays.fill(factors, 1.0);
        var byEvent = new LinkedHashMap<Integer, Double>();
        var exhausted = new LinkedHashSet<Integer>();
        var total = 0.0;
        for (var event : multipliers) {
            var parts = new double[paying.size()];
            var wanted = 0.0;
            for (var index = 0; index < paying.size(); index++) {
                var line = paying.get(index);
                if (!event.covers(line.material())) continue;
                var raised = Math.min(cap, factors[index] * event.value());
                parts[index] = Math.max(0, line.gross() * (raised - factors[index]));
                wanted += parts[index];
            }
            if (!(wanted > 0)) continue;
            var limited = limit.applyAsDouble(event);
            var available = limited > 0 ? limited : 0;
            var share = available >= wanted ? 1.0 : available / wanted;
            if (share < 1.0) exhausted.add(event.id());
            var pays = 0.0;
            for (var index = 0; index < paying.size(); index++) {
                if (parts[index] <= 0) continue;
                var part = parts[index] * share;
                if (share < 1.0) part = Math.min(part, Math.max(0, available - pays));
                factors[index] += part / paying.get(index).gross();
                pays += part;
            }
            if (share < 1.0) pays = Math.min(pays, available);
            paid.accept(event, pays);
            if (pays > 0) byEvent.put(event.id(), pays);
            total += pays;
        }
        return new Extra(total, byEvent, exhausted);
    }

    private static double sum(Collection<ServerEvent> events, EventKind kind) {
        return events.stream()
                .filter(event -> event.kind() == kind)
                .mapToDouble(ServerEvent::value)
                .sum();
    }

    /**
     * The gross payout of one line of an order.
     *
     * @param material the material of the line
     * @param gross    what the line pays before tax
     */
    public record Line(String material, double gross) {}

    /**
     * What the multipliers pay on top of a payout.
     *
     * @param total     the extra in all
     * @param byEvent   the part of each event that pays, by event id, in the order the events started
     * @param exhausted the events that could pay less than their part, which run out of money with this payout
     */
    public record Extra(double total, Map<Integer, Double> byEvent, Set<Integer> exhausted) {
        /**
         * @param total     the extra in all
         * @param byEvent   the part of each event
         * @param exhausted the events that could pay less than their part
         */
        public Extra {
            byEvent = Collections.unmodifiableMap(new LinkedHashMap<>(byEvent));
            exhausted = Collections.unmodifiableSet(new LinkedHashSet<>(exhausted));
        }
    }
}
