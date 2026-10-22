/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.data.wrapper.order.FullOrder;

import java.util.Map;

/**
 * What a delivery recorded.
 *
 * @param deliveredByLine recorded amount per order line; lines without a recorded item are left out
 * @param order           the order as it is after the delivery
 * @param orderDone       whether the delivery completed the order
 */
public record DeliveryResult(Map<Integer, Integer> deliveredByLine, FullOrder order, boolean orderDone) {
    /**
     * @return number of items recorded on all lines
     */
    public int delivered() {
        return deliveredByLine.values().stream().mapToInt(Integer::intValue).sum();
    }
}
