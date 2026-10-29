/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.data.wrapper.offer.OfferMark;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.orders.SystemOwner;
import net.kyori.adventure.translation.Translatable;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public enum SortingType implements Translatable {
    AGE(Comparator.comparing(SimpleOrder::created)),
    NAME((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.name(), o2.name())),
    PRICE(Comparator.comparingDouble(FullOrder::price)),
    SIZE(Comparator.comparingInt(FullOrder::amount)),
    UNIT_PRICE(Comparator.comparingDouble(SortingType::unitPrice)),
    /**
     * By the end of the event offer an order belongs to, the soonest first; orders of no offer follow by age. The
     * repository sorts by age, the search then by the ends it looks up, see {@link #byOfferEnd}.
     */
    OFFER_END(Comparator.comparing(SimpleOrder::created));

    private final Comparator<FullOrder> comparator;

    SortingType(Comparator<FullOrder> comparator) {
        this.comparator = comparator;
    }

    private static double unitPrice(FullOrder order) {
        var amount = order.amount();
        return amount == 0 ? 0 : order.price() / amount;
    }

    /**
     * Sorts orders in place.
     *
     * @param orders       the orders
     * @param asc          true for ascending
     * @param playersFirst true to put orders of players before orders of the server that sort the same
     */
    public void sort(List<FullOrder> orders, boolean asc, boolean playersFirst) {
        var order = asc ? comparator : comparator.reversed();
        if (playersFirst) order = order.thenComparing(found -> SystemOwner.is(found.owner()));
        orders.sort(order);
    }

    /**
     * Sorts orders by the end of their event offers in place, keeping the order of orders that end at the same time or
     * belong to no offer, which come last either way.
     *
     * @param orders the orders, sorted otherwise already
     * @param marks  the event offer of each order that belongs to one, by order id
     * @param asc    true for the soonest end first
     */
    public static void byOfferEnd(List<FullOrder> orders, Map<Integer, OfferMark> marks, boolean asc) {
        Comparator<LocalDateTime> ends = asc ? Comparator.naturalOrder() : Comparator.reverseOrder();
        orders.sort(Comparator.comparing(
                (FullOrder order) -> {
                    var mark = marks.get(order.id());
                    return mark == null ? null : mark.ends();
                },
                Comparator.nullsLast(ends)));
    }

    @Override
    public @NotNull String translationKey() {
        return "enums.sortingType." + name().toLowerCase(Locale.ROOT);
    }
}
