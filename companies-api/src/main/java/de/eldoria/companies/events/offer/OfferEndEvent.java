/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.offer;

import de.eldoria.companies.components.offer.IEventOffer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A goal or a contest that ran closed. An offer closed before it ever started fires nothing.
 * <p>
 * Fired once in the whole network, on the server where it closed: the primary server when its end passed, when a goal
 * reached its amount or ran out of money, or when its server event closed; the server of the delivery that filled a
 * contest or spent its money; the server where an admin cancelled it. A contest's places and prizes follow later with
 * {@link ContestSettledEvent}.
 */
public class OfferEndEvent extends OfferEvent {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final Reason reason;

    /**
     * Creates a new event.
     *
     * @param offer  the offer, closed
     * @param reason why it closed
     */
    public OfferEndEvent(IEventOffer offer, Reason reason) {
        super(offer);
        this.reason = Objects.requireNonNull(reason, "reason");
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
     * Why the offer closed.
     *
     * @return reason
     */
    public Reason reason() {
        return reason;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Why an offer closed.
     */
    public enum Reason {
        /**
         * Its whole amount was delivered.
         */
        REACHED,
        /**
         * Its end passed.
         */
        ENDED,
        /**
         * Its budget could not pay what was still missing.
         */
        BUDGET_SPENT,
        /**
         * An admin cancelled it, or it was cancelled with its server event.
         */
        CANCELLED
    }
}
