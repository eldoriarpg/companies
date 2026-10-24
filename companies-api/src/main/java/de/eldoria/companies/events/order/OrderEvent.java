/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.order;

import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.events.components.OrderProvider;
import de.eldoria.companies.events.components.OwnerProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;

/**
 * Base of the events about an existing order. Listen to its subclasses; this class has no handler list.
 * <p>
 * <b>Events carry both sides of an order. Companies keeps orders anonymous: never show the owner to the company's
 * members or the company to the owner. Use both sides only for logic, logging or staff tools.</b>
 * <p>
 * Order events fire only on the server where the action happened. Expiry and removal of unclaimed orders happen on
 * the primary server of a network.
 *
 * @param <Order> type of order
 */
public abstract class OrderEvent<Order extends ISimpleOrder> extends Event
        implements OrderProvider<Order>, OwnerProvider {
    private final Order order;

    /**
     * Creates a synchronous event.
     *
     * @param order the order
     */
    public OrderEvent(Order order) {
        super(false);
        this.order = order;
    }

    /**
     * Creates an event.
     *
     * @param order the order
     * @param async whether the event is fired off the main thread
     */
    public OrderEvent(Order order, boolean async) {
        super(async);
        this.order = order;
    }

    @Override
    public Order order() {
        return order;
    }

    /**
     * The player who placed the order.
     *
     * @return owner of the order, looked up by {@link ISimpleOrder#owner()}
     */
    @Override
    public OfflinePlayer owner() {
        return Bukkit.getOfflinePlayer(order.owner());
    }
}
