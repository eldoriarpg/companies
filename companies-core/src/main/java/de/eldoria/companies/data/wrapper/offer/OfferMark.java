/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.offer;

import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;

/**
 * What players see of the offer an order belongs to: the event badge with the offer's name.
 *
 * @param offer     id of the offer
 * @param name      name of the offer
 * @param ends      when the offer ends
 * @param state     state of the offer
 * @param amount    items the offer asks for in all
 * @param delivered items delivered so far
 * @param event     id of the server event the offer belongs to, null for a standalone offer
 */
public record OfferMark(
        int offer,
        String name,
        LocalDateTime ends,
        OfferState state,
        int amount,
        int delivered,
        @Nullable Integer event) {
    /**
     * The mark of an offer of no server event.
     *
     * @param offer     id of the offer
     * @param name      name of the offer
     * @param ends      when it ends
     * @param state     where it is in its life
     * @param amount    items asked for in all
     * @param delivered items delivered so far
     */
    public OfferMark(int offer, String name, LocalDateTime ends, OfferState state, int amount, int delivered) {
        this(offer, name, ends, state, amount, delivered, null);
    }

    /**
     * @return the share delivered, from 0 to 100
     */
    public int percent() {
        return EventOffer.percent(delivered, amount);
    }
}
