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
 * This event may be executed multiple times for the same order. The worker receives {@link #amount()}, which is the
 * {@link #gross()} share of the order price minus the {@link #tax()} of the company. A cancelled payment is neither paid
 * nor taxed.
 * <p>
 * Orders are anonymous: the worker must not learn who placed the order.
 */
public class OrderPaymentEvent extends OrderEvent<ISimpleOrder> implements Cancellable, PlayerProvider<OfflinePlayer> {
    public static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer player;
    private final double amount;
    private final double gross;
    private final double tax;
    private boolean cancelled;

    /**
     * Creates a payment without tax.
     *
     * @param order  order which is paid
     * @param player worker receiving the payment
     * @param amount amount paid
     */
    public OrderPaymentEvent(ISimpleOrder order, OfflinePlayer player, double amount) {
        this(order, player, amount, amount, 0.0);
    }

    /**
     * Creates a taxed payment.
     *
     * @param order  order which is paid
     * @param player worker receiving the payment
     * @param amount amount paid after tax
     * @param gross  share of the order price before tax
     * @param tax    tax taken from the share
     */
    public OrderPaymentEvent(ISimpleOrder order, OfflinePlayer player, double amount, double gross, double tax) {
        super(order, true);
        this.player = player;
        this.amount = amount;
        this.gross = gross;
        this.tax = tax;
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

    /**
     * The amount the worker receives, after tax.
     *
     * @return net amount
     */
    public double amount() {
        return amount;
    }

    /**
     * The worker's share of the order price before tax.
     *
     * @return gross amount
     */
    public double gross() {
        return gross;
    }

    /**
     * The tax taken from the worker's share.
     *
     * @return tax amount
     */
    public double tax() {
        return tax;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
