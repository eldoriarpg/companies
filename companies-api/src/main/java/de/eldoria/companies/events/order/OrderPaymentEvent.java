/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.order;

import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.events.components.CompanyProvider;
import de.eldoria.companies.events.components.PlayerProvider;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A worker is about to be paid their share of a completed order.
 * <p>
 * Fired once per worker of the order, off the main thread. The worker receives {@link #amount()}, which is the
 * {@link #gross()} share of the order price minus the {@link #tax()} of the company. A cancelled payment is neither paid
 * nor taxed. It fires only on the server where the order was completed.
 * <p>
 * Orders are anonymous: the worker must not learn who placed the order.
 */
public class OrderPaymentEvent extends OrderEvent<ISimpleOrder>
        implements Cancellable, PlayerProvider<OfflinePlayer>, CompanyProvider<ISimpleCompany> {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final ISimpleCompany company;
    private final OfflinePlayer player;
    private final double amount;
    private final double gross;
    private final double tax;
    private boolean cancelled;

    /**
     * Creates a payment without tax. The company is known only by the id stored in the order.
     *
     * @param order  order which is paid
     * @param player worker receiving the payment
     * @param amount amount paid
     */
    public OrderPaymentEvent(ISimpleOrder order, OfflinePlayer player, double amount) {
        this(order, player, amount, amount, 0.0);
    }

    /**
     * Creates a taxed payment. The company is known only by the id stored in the order.
     *
     * @param order  order which is paid
     * @param player worker receiving the payment
     * @param amount amount paid after tax
     * @param gross  share of the order price before tax
     * @param tax    tax taken from the share
     */
    public OrderPaymentEvent(ISimpleOrder order, OfflinePlayer player, double amount, double gross, double tax) {
        this(order, ISimpleCompany.forId(order.company()), player, amount, gross, tax);
    }

    /**
     * Creates a taxed payment.
     *
     * @param order   order which is paid
     * @param company company that completed the order
     * @param player  worker receiving the payment
     * @param amount  amount paid after tax
     * @param gross   share of the order price before tax
     * @param tax     tax taken from the share
     */
    public OrderPaymentEvent(
            ISimpleOrder order, ISimpleCompany company, OfflinePlayer player, double amount, double gross, double tax) {
        super(order, true);
        this.company = Objects.requireNonNull(company, "company");
        this.player = player;
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
     * The company that completed the order. Created with a constructor without a company, only its
     * {@link ISimpleCompany#id() id} is known, as with {@link ISimpleCompany#forId(int)}.
     *
     * @return company
     */
    @Override
    public ISimpleCompany company() {
        return company;
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
