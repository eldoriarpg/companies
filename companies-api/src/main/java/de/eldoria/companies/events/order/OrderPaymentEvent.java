/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.order;

import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.events.components.PlayerProvider;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A payment for a order was retrieved by one user.
 * <p>
 * This event may be executed multiple times for the same order.
 */
public class OrderPaymentEvent extends OrderEvent<ISimpleOrder> implements Cancellable, PlayerProvider<OfflinePlayer> {
    public static HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer player;
    private final double amount;
    private boolean cancelled;

    public OrderPaymentEvent(ISimpleOrder order, OfflinePlayer player, double amount) {
        super(order, true);
        this.player = player;
        this.amount = amount;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }

    public OfflinePlayer player() {
        return player;
    }

    public double amount() {
        return amount;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
