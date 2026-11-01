/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.offer;

import de.eldoria.companies.components.offer.ContestPlace;
import de.eldoria.companies.components.offer.IEventOffer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A closed contest was settled: its places are fixed and its prizes paid. A cancelled contest pays no prizes and fires
 * nothing.
 * <p>
 * Fired once in the whole network, on the primary server, which settles contests.
 */
public class ContestSettledEvent extends OfferEvent {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final List<ContestPlace> places;

    /**
     * Creates a new event.
     *
     * @param offer  the contest, settled
     * @param places the companies that delivered and still exist, in the order of their places
     */
    public ContestSettledEvent(IEventOffer offer, List<ContestPlace> places) {
        super(offer);
        this.places = List.copyOf(places);
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
     * The places of the contest, first place first, with the prize each was paid.
     *
     * @return the places
     */
    public List<ContestPlace> places() {
        return places;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
