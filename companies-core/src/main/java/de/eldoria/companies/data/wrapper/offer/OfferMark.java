/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.offer;

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
 */
public record OfferMark(int offer, String name, LocalDateTime ends, OfferState state, int amount, int delivered) {
    /**
     * @return the share delivered, from 0 to 100
     */
    public int percent() {
        return EventOffer.percent(delivered, amount);
    }
}
