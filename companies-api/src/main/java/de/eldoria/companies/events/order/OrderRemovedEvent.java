/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.order;

import de.eldoria.companies.components.order.ISimpleOrder;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * An order was deleted before it was delivered: removed because nobody accepted it, cancelled by its owner, or deleted
 * by an admin. The {@link #reason()} tells which.
 * <p>
 * Fired after the order was deleted, off the main thread. It fires only on the server where the order was removed;
 * removals because nobody accepted the order happen on the primary server of a network.
 * <p>
 * A claimed order that the company gives up fires {@link OrderCanceledEvent}, one that runs out of time fires
 * {@link OrderExpiredEvent}.
 */
public class OrderRemovedEvent extends OrderEvent<ISimpleOrder> {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final Reason reason;

    /**
     * Creates a new event.
     *
     * @param order  the deleted order
     * @param reason why the order was deleted
     */
    public OrderRemovedEvent(ISimpleOrder order, Reason reason) {
        super(order, true);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * The handlers of this event.
     *
     * @return handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * Why the order was deleted.
     *
     * @return reason
     */
    public Reason reason() {
        return reason;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Why an order was deleted.
     */
    public enum Reason {
        /**
         * Nobody accepted the order in time. The owner was refunded what they paid.
         */
        PURGED,
        /**
         * The owner cancelled the unclaimed order.
         */
        CANCELLED,
        /**
         * An admin deleted the order.
         */
        ADMIN
    }
}
