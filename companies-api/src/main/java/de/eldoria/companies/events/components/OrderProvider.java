/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.components;

import de.eldoria.companies.components.order.ISimpleOrder;

@FunctionalInterface
public interface OrderProvider<O extends ISimpleOrder> {
    /**
     * The order associated with this event/
     *
     * @return order
     */
    O order();
}
