/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.data.wrapper.stats.MemberStats;
import net.kyori.adventure.translation.Translatable;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * What the member list is sorted by. By name the owner comes first; by a contribution the highest value comes first.
 */
public enum MemberSorting implements Translatable {
    /**
     * The owner first, then the members by name.
     */
    NAME(stats -> 0),
    /**
     * Items delivered to completed orders.
     */
    ITEMS(MemberStats::items),
    /**
     * Money paid out after tax.
     */
    EARNED(MemberStats::earned),
    /**
     * Completed orders the member delivered to.
     */
    ORDERS(MemberStats::orders);

    private final ToDoubleFunction<MemberStats> value;

    MemberSorting(ToDoubleFunction<MemberStats> value) {
        this.value = value;
    }

    /**
     * The sorting with the given id.
     *
     * @param id id as returned by {@link #id()}, in any case
     * @return the sorting, or empty when no sorting has this id
     */
    public static Optional<MemberSorting> byId(String id) {
        for (var sorting : values()) if (sorting.id().equalsIgnoreCase(id)) return Optional.of(sorting);
        return Optional.empty();
    }

    /**
     * @return the id used in commands, the name in lower case
     */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The value a member is sorted by.
     *
     * @param stats contribution of the member
     * @return the value, 0 for {@link #NAME}
     */
    public double value(MemberStats stats) {
        return value.applyAsDouble(stats);
    }

    /**
     * @return the sorting after this one, back to the first after the last
     */
    public MemberSorting next() {
        return values()[(ordinal() + 1) % values().length];
    }

    @Override
    public @NotNull String translationKey() {
        return "enums.memberSorting." + id();
    }
}
