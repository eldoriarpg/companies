/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.order.IOrderContent;

import java.util.Comparator;
import java.util.List;

/**
 * The order in which the lines of an order are filled when items go to several lines at once.
 */
public final class DeliveryOrder {
    private DeliveryOrder() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Puts exact lines first, then material lines, then criteria lines, and keeps the order within each. The lines
     * accepting the fewest items go first, so the wider lines get what the narrower ones can not take.
     *
     * @param lines the lines of an order
     * @param <T>   the type of the lines
     * @return the lines in the order to fill them
     */
    public static <T extends IOrderContent> List<T> narrowFirst(List<T> lines) {
        return lines.stream()
                .sorted(Comparator.comparingInt(DeliveryOrder::rank))
                .toList();
    }

    private static int rank(IOrderContent line) {
        return switch (line.kind()) {
            case EXACT -> 0;
            case MATERIAL -> 1;
            case CRITERIA -> 2;
        };
    }
}
