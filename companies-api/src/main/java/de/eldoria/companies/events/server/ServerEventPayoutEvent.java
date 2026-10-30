/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.server;

import de.eldoria.companies.components.event.IServerEvent;
import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.events.components.PlayerProvider;
import de.eldoria.companies.events.order.OrderEvent;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Server events paid a worker extra on top of the payout of a completed order.
 * <p>
 * Fired off the main thread, after the {@link de.eldoria.companies.events.order.OrderPaymentEvent} or
 * {@link de.eldoria.companies.events.order.SoloOrderPaymentEvent} of the worker was not cancelled and the reward
 * multipliers paid, on the server where the order was completed. The money is taken already, so the event can not be
 * cancelled. The extra is neither taxed nor shared with the company's treasury; tax reductions show in the payment
 * events' tax.
 * <p>
 * Orders are anonymous: the worker must not learn who placed the order.
 */
public class ServerEventPayoutEvent extends OrderEvent<ISimpleOrder> implements PlayerProvider<OfflinePlayer> {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer player;
    private final double amount;
    private final List<Paid> paid;

    /**
     * Creates a new event.
     *
     * @param order  order which is paid
     * @param player worker receiving the extra
     * @param amount extra paid on top of the payout
     * @param paid   what each reward multiplier paid the worker
     */
    public ServerEventPayoutEvent(ISimpleOrder order, OfflinePlayer player, double amount, List<Paid> paid) {
        super(order, true);
        this.player = Objects.requireNonNull(player, "player");
        this.amount = amount;
        this.paid = List.copyOf(paid);
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
     * The worker receiving the extra.
     *
     * @return worker
     */
    @Override
    public OfflinePlayer player() {
        return player;
    }

    /**
     * The extra paid on top of the payout.
     *
     * @return amount
     */
    public double amount() {
        return amount;
    }

    /**
     * What each reward multiplier paid the worker, only those that paid something. The events are a snapshot read
     * right before they paid, so their {@link IServerEvent#spent()} does not count this payout yet.
     *
     * @return the parts of {@link #amount()}, by event
     */
    public List<Paid> paid() {
        return paid;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * What one reward multiplier paid the worker.
     *
     * @param event  the server event
     * @param amount what it paid
     */
    public record Paid(IServerEvent event, double amount) {
        /**
         * Creates the part.
         */
        public Paid {
            Objects.requireNonNull(event, "event");
        }
    }
}
