/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.order;

import de.eldoria.companies.components.order.IFullOrder;
import de.eldoria.companies.events.components.PlayerProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * The owner received the items of an order a player delivered alone.
 * <p>
 * Fired after the order changed to {@link de.eldoria.companies.components.order.OrderState#RECEIVED}, usually off the
 * main thread; the event may be asynchronous. It fires only on the server where the owner received the order. An order
 * a company delivered fires {@link OrderReceivedEvent} instead.
 * <p>
 * Orders are anonymous: never show the {@link #player() worker} to the owner.
 */
public class SoloOrderReceivedEvent extends OrderEvent<IFullOrder> implements PlayerProvider<OfflinePlayer> {
    private static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer worker;

    /**
     * Creates a new event. It is asynchronous when created off the main thread.
     *
     * @param order  the received order
     * @param worker player who delivered the order
     */
    public SoloOrderReceivedEvent(IFullOrder order, OfflinePlayer worker) {
        super(order, !Bukkit.isPrimaryThread());
        this.worker = Objects.requireNonNull(worker, "worker");
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
