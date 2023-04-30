/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.order;

import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.events.components.OrderProvider;
import org.bukkit.event.Event;

/**
 * Represends a basic order event.
 * <p>
 * This event is for internal use and should not be subscribed.
 *
 * @param <Order> Type of order implementation
 */
public abstract class OrderEvent<Order extends ISimpleOrder> extends Event implements OrderProvider<Order> {
    private final Order order;

    public OrderEvent(Order order) {
        super(false);
        this.order = order;
    }

    public OrderEvent(Order order, boolean async) {
        super(async);
        this.order = order;
    }

    @Override
    public Order order() {
        return order;
    }
}
