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
 * A player claimed an order to work on it alone, without a company.
 * <p>
 * Fired after the order was claimed, off the main thread. It fires only on the server where the order was accepted.
 * {@link PreSoloOrderAcceptEvent} is fired before and can prevent it. A company claiming an order fires
 * {@link OrderAcceptEvent} instead.
 * <p>
 * Orders are anonymous: never show the owner to the worker or the worker to the owner.
 */
public class SoloOrderAcceptEvent extends OrderEvent<ISimpleOrder> implements PlayerProvider<OfflinePlayer> {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer worker;

    /**
     * Creates a new event.
     *
     * @param order  the claimed order
     * @param worker player who claimed the order
     */
    public SoloOrderAcceptEvent(ISimpleOrder order, OfflinePlayer worker) {
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
     * The player who claimed the order.
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
