/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.order.IOrderContent;
import de.eldoria.companies.components.order.LineKind;

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
     * Puts material and exact lines before criteria lines and keeps the order within each. A criteria line accepts
     * more items than the others, so filling it last leaves it what the narrower lines can not take.
     *
     * @param lines the lines of an order
     * @param <T>   the type of the lines
     * @return the lines in the order to fill them
     */
    public static <T extends IOrderContent> List<T> narrowFirst(List<T> lines) {
        return lines.stream()
                .sorted(Comparator.comparing(line -> line.kind() == LineKind.CRITERIA))
                .toList();
    }
}
