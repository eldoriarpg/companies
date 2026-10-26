/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.orders.SystemOwner;
import net.kyori.adventure.translation.Translatable;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public enum SortingType implements Translatable {
    AGE(Comparator.comparing(SimpleOrder::created)),
    NAME((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.name(), o2.name())),
    PRICE(Comparator.comparingDouble(FullOrder::price)),
    SIZE(Comparator.comparingInt(FullOrder::amount)),
    UNIT_PRICE(Comparator.comparingDouble(SortingType::unitPrice));

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

    @Override
    public @NotNull String translationKey() {
        return "enums.sortingType." + name().toLowerCase(Locale.ROOT);
    }
}
