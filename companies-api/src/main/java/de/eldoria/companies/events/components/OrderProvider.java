/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.components;

import de.eldoria.companies.components.order.ISimpleOrder;

/**
 * An event that involves an order.
 *
 * @param <O> type of order
 */
@FunctionalInterface
public interface OrderProvider<O extends ISimpleOrder> {
    /**
     * The order involved in the event.
     *
     * @return order
     */
    O order();
}
