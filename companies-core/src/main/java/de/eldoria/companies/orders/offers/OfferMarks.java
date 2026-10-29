/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.offers;

import de.eldoria.companies.data.wrapper.offer.OfferMark;

import java.util.Collection;
import java.util.Map;

/**
 * Finds the event offers orders belong to, so every place that lists orders marks them.
 */
@FunctionalInterface
public interface OfferMarks {
    /**
     * Knows no offers.
     */
    OfferMarks NONE = orders -> Map.of();

    /**
     * @param orders ids of orders
     * @return the offer of each order that belongs to one, by order id
     */
    Map<Integer, OfferMark> marks(Collection<Integer> orders);
}
