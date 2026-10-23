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

import java.util.Objects;

/**
 * A player who completed an order alone is about to be paid.
 * <p>
 * Fired once per completed solo order, off the main thread. The worker receives {@link #amount()}, which is the
 * {@link #gross() price} of the delivered items minus the {@link #tax()} of solo orders; there is no treasury share.
 * A cancelled payment is neither paid nor taxed. It fires only on the server where the order was completed. A worker
 * paid for an order of a company fires {@link OrderPaymentEvent} instead.
 * <p>
 * Orders are anonymous: the worker must not learn who placed the order.
 */
public class SoloOrderPaymentEvent extends OrderEvent<ISimpleOrder>
        implements Cancellable, PlayerProvider<OfflinePlayer> {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer player;
    private final double amount;
    private final double gross;
    private final double tax;
    private boolean cancelled;

    /**
     * Creates a new event.
     *
     * @param order  order which is paid
     * @param player worker receiving the payment
     * @param amount amount paid after tax
     * @param gross  price of the delivered items before tax
     * @param tax    tax taken from the price
     */
    public SoloOrderPaymentEvent(ISimpleOrder order, OfflinePlayer player, double amount, double gross, double tax) {
        super(order, true);
        this.player = Objects.requireNonNull(player, "player");
        this.amount = amount;
        this.gross = gross;
        this.tax = tax;
    }

    /**
     * The handlers of this event.
     *
     * @return handler list
     */
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

    /**
     * The worker receiving the payment.
     *
     * @return worker
     */
    @Override
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
     * The price of the items the worker delivered, before tax.
     *
     * @return gross amount
     */
    public double gross() {
        return gross;
    }

    /**
     * The tax taken from the price.
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
