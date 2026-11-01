/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.offer;

import de.eldoria.companies.components.offer.IEventOffer;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;

import java.util.Objects;

/**
 * An event about an event offer. It is asynchronous when fired off the main thread.
 */
public abstract class OfferEvent extends Event {
    private final IEventOffer offer;

    /**
     * Creates a new event, asynchronous when created off the main thread.
     *
     * @param offer the offer, as it is now
     */
    protected OfferEvent(IEventOffer offer) {
        super(!Bukkit.isPrimaryThread());
        this.offer = Objects.requireNonNull(offer, "offer");
    }

    /**
     * The offer.
     *
     * @return the offer as it was when the event was fired
     */
    public IEventOffer offer() {
        return offer;
    }
}
