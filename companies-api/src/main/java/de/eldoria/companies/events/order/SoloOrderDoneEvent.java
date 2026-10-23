/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.order;

import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.events.components.PlayerProvider;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A player delivered every item of an order they worked on alone, and the owner can receive it.
 * <p>
 * Fired after the order changed to {@link de.eldoria.companies.components.order.OrderState#DELIVERED}, off the main
 * thread. It fires only on the server where the last delivery happened. The worker is paid afterwards, see
 * {@link SoloOrderPaymentEvent}. An order a company delivered fires {@link OrderDoneEvent} instead.
 * <p>
 * Orders are anonymous: never show the owner to the worker or the worker to the owner.
 */
public class SoloOrderDoneEvent extends OrderEvent<ISimpleOrder> implements PlayerProvider<OfflinePlayer> {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer worker;

    /**
     * Creates a new event.
     *
     * @param order  the delivered order
     * @param worker player who delivered the order
     */
    public SoloOrderDoneEvent(ISimpleOrder order, OfflinePlayer worker) {
        super(order, true);
        this.worker = Objects.requireNonNull(worker, "worker");
    }

    /**
     * The handlers of this event.
     *
     * @return handler list
     */
    @SuppressWarnings("SameReturnValue")
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * The player who delivered the order.
     *
     * @return worker
     */
    @Override
    public OfflinePlayer player() {
        return worker;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
