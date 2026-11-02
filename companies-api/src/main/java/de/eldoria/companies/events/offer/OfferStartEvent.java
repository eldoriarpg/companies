/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.offer;

import de.eldoria.companies.components.offer.IEventOffer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A goal or a contest started, the first time it runs; resuming a paused offer fires nothing.
 * <p>
 * Fired once in the whole network, on the primary server, which starts offers.
 */
public class OfferStartEvent extends OfferEvent {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    /**
     * Creates a new event.
     *
     * @param offer the offer that started
     */
    public OfferStartEvent(IEventOffer offer) {
        super(offer);
    }

    /**
     * The handlers of this event.
     *
     * @return handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
