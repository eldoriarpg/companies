/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import java.util.List;

/**
 * One page of a list shown in a menu.
 *
 * @param items  entries on this page
 * @param number index of this page, from zero
 * @param count  number of pages, at least one even for an empty list
 * @param <T>    type of the entries
 */
public record Page<T>(List<T> items, int number, int count) {
    /**
     * Cuts a page out of a list. A page beyond the last one shows the last one, a negative page the first one.
     *
     * @param entries   the whole list
     * @param requested index of the page asked for, from zero
     * @param size      entries per page
     * @param <T>       type of the entries
     * @return the page
     * @throws IllegalArgumentException when the size is not positive
     */
    public static <T> Page<T> of(List<T> entries, int requested, int size) {
        if (size < 1) throw new IllegalArgumentException("Page size must be positive: " + size);
        var count = Math.max(1, (entries.size() + size - 1) / size);
        var number = Math.clamp(requested, 0, count - 1);
        var start = number * size;
        var items = List.copyOf(entries.subList(start, Math.min(start + size, entries.size())));
        return new Page<>(items, number, count);
    }

    /**
     * @return whether a page comes before this one
     */
    public boolean hasPrevious() {
        return number > 0;
    }

    /**
     * @return whether a page follows this one
     */
    public boolean hasNext() {
        return number < count - 1;
    }
}
