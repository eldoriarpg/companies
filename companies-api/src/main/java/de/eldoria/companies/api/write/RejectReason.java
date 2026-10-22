/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

/**
 * Why an order was not created.
 */
public enum RejectReason {
    /**
     * The owner has reached their limit of active orders.
     */
    LIMIT_REACHED,
    /**
     * The order has more lines than the server allows.
     */
    TOO_MANY_LINES,
    /**
     * The order requests more items than the server allows.
     */
    TOO_MANY_ITEMS,
    /**
     * The owner can not pay the price and the fee.
     */
    INSUFFICIENT_FUNDS,
    /**
     * A listener cancelled the {@link de.eldoria.companies.events.order.PreOrderCreateEvent}.
     */
    CANCELLED,
    /**
     * The order is not valid, for example its name is too long, or a listener of the
     * {@link de.eldoria.companies.events.order.PreOrderCreateEvent} left it without lines.
     */
    INVALID
}
