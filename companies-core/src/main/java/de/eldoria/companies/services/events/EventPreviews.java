/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import de.eldoria.companies.components.event.EventKind;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.orders.OrderMaterials;
import de.eldoria.companies.orders.Payout;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * What the server events running now mean for an order, as players see it before they work on it: the marks of the
 * order search and the expected payout of the order view.
 * <p>
 * Both are previews: they use the events as this node sees them now, and for a reward multiplier what is left of its
 * budget, but not the balance of the account paying it. They hold only if the order is completed before the end of the
 * soonest of those events, which they name.
 */
public final class EventPreviews {
    private EventPreviews() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * The marks of found orders.
     *
     * @param orders the orders
     * @param level  the level of the searching company, empty for a player working alone
     * @param active the events of this node
     * @param cap    the cap of all multipliers together
     * @param at     the current time in UTC
     * @return the mark of each order an event boosts, by order id
     */
    public static Map<Integer, EventMark> marks(
            List<FullOrder> orders, OptionalInt level, ActiveEvents active, double cap, LocalDateTime at) {
        Map<Integer, EventMark> marks = new HashMap<>();
        if (orders.isEmpty() || active.activeAt(at).isEmpty()) return marks;
        for (var order : orders) {
            mark(order, active.matching(at, OrderMaterials.accepted(order), level), cap)
                    .ifPresent(mark -> marks.put(order.id(), mark));
        }
        return marks;
    }

    /**
     * What the events matching an order do to it: the highest factor on one of its lines, the tax taken off and the
     * fee taken off.
     *
     * @param order    the order
     * @param matching the events that apply to the order
     * @param cap      the cap of all multipliers together
     * @return the mark, empty when no event changes the order
     */
    public static Optional<EventMark> mark(FullOrder order, List<ServerEvent> matching, double cap) {
        if (matching.isEmpty()) return Optional.empty();
        var multiplier = 1.0;
        for (var content : order.contents()) {
            var accepted = OrderMaterials.accepted(content);
            var covering = matching.stream()
                    .filter(event -> event.kind() == EventKind.REWARD_MULTIPLIER)
                    .filter(event -> accepted.stream().anyMatch(event::covers))
                    .toList();
            multiplier = Math.max(multiplier, EventEffects.multiplier(covering, cap));
        }
        var tax = matching.stream()
                .filter(event -> event.kind() == EventKind.TAX_REDUCTION)
                .mapToDouble(ServerEvent::value)
                .sum();
        var fee = Math.min(
                ServerEvent.MAX_REDUCTION,
                matching.stream()
                        .filter(event -> event.kind() == EventKind.FEE_REDUCTION)
                        .mapToDouble(ServerEvent::value)
                        .sum());
        if (!(multiplier > 1) && !(tax > 0) && !(fee > 0)) return Optional.empty();
        return Optional.of(new EventMark(multiplier, tax, fee, soonestEnd(matching)));
    }

    /**
     * The payout of a whole order with the events that apply to it, for a company of a tax or a player working alone.
     *
     * @param order    the order
     * @param matching the events that apply to the order
     * @param baseTax  the tax without events, in percent
     * @param cap      the cap of all multipliers together
     * @return the payout and until when it holds, empty when no event changes the payout
     */
    public static Optional<Preview> preview(FullOrder order, List<ServerEvent> matching, double baseTax, double cap) {
        var paying = matching.stream()
                .filter(event -> event.kind() != EventKind.FEE_REDUCTION)
                .toList();
        if (paying.isEmpty()) return Optional.empty();
        var tax = EventEffects.taxPercent(baseTax, paying);
        List<EventEffects.Line> lines = new ArrayList<>();
        for (var content : order.contents())
            lines.add(new EventEffects.Line(material(content, paying), content.price()));
        var extra = EventEffects.extra(
                lines, paying, cap, event -> event.remaining().orElse(Double.POSITIVE_INFINITY));
        var net = Payout.split(order.price(), tax).net();
        if (tax == baseTax && !(extra.total() > 0)) return Optional.empty();
        return Optional.of(new Preview(net + extra.total(), soonestEnd(paying)));
    }

    /**
     * The material a line pays on for the preview: its own, or for a criteria line one it accepts that a multiplier
     * covers.
     */
    private static String material(OrderContent content, List<ServerEvent> events) {
        var own = content.material().name();
        var multipliers = events.stream()
                .filter(event -> event.kind() == EventKind.REWARD_MULTIPLIER)
                .toList();
        if (multipliers.stream().anyMatch(event -> event.covers(own))) return own;
        for (var accepted : OrderMaterials.accepted(content)) {
            if (multipliers.stream().anyMatch(event -> event.covers(accepted))) return accepted;
        }
        return own;
    }

    private static @Nullable LocalDateTime soonestEnd(List<ServerEvent> events) {
        return events.stream()
                .map(ServerEvent::ends)
                .min(LocalDateTime::compareTo)
                .orElse(null);
    }

    /**
     * What events do to an order in the order search.
     *
     * @param multiplier the highest factor on one of its lines, 1 for none
     * @param taxPoints  the percentage points taken off the tax
     * @param feePercent the percent taken off the fee
     * @param ends       the end of the soonest of these events
     */
    public record EventMark(
            double multiplier,
            double taxPoints,
            double feePercent,
            @Nullable LocalDateTime ends) {}

    /**
     * The expected payout of an order with the events running now.
     *
     * @param net  what the order pays its workers in all, with the events
     * @param ends until when it holds: the end of the soonest of these events
     */
    public record Preview(double net, @Nullable LocalDateTime ends) {}
}
