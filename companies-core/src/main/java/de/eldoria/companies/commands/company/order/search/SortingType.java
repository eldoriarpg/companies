/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import net.kyori.adventure.translation.Translatable;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public enum SortingType implements Translatable {
    AGE(Comparator.comparing(SimpleOrder::created)),
    NAME((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.name(), o2.name())),
    PRICE(Comparator.comparingDouble(FullOrder::price)),
    SIZE(Comparator.comparingInt(FullOrder::amount));

    private final Comparator<FullOrder> comparator;

    SortingType(Comparator<FullOrder> comparator) {
        this.comparator = comparator;
    }

    public void sort(List<FullOrder> orders, boolean asc) {
        orders.sort(comparator);
        if (!asc) {
            Collections.reverse(orders);
        }
    }

    @Override
    public @NotNull String translationKey() {
        return "enums.sortingType." + name().toLowerCase(Locale.ROOT);
    }
}
