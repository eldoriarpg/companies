/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.order;

import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.events.components.PlayerProvider;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A player gave up an order they worked on alone; the order is open again.
 * <p>
 * Fired after the order was released and the delivered items went back to the worker, off the main thread. It fires
 * only on the server where the order was aborted. A company giving up an order fires {@link OrderCanceledEvent}
 * instead.
 * <p>
 * Orders are anonymous: never show the owner to the worker or the worker to the owner.
 */
public class SoloOrderAbortEvent extends OrderEvent<ISimpleOrder> implements PlayerProvider<OfflinePlayer> {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer worker;

    /**
     * Creates a new event.
     *
     * @param order  the released order
     * @param worker player who gave the order up
     */
    public SoloOrderAbortEvent(ISimpleOrder order, OfflinePlayer worker) {
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
     * The player who gave the order up.
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
