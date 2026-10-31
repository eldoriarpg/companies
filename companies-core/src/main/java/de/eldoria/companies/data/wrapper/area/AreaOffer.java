/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.area;

import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A plot or region the server offers companies to buy or rent, stored in {@code area_offers}. An offer stays while a
 * company has the area and is open again when the area goes back to the server.
 *
 * @param id         id of the offer, 0 before it is stored
 * @param provider   id of the area provider, lower case
 * @param ref        the provider's reference of the area
 * @param world      the world it lies in
 * @param node       the server whose world holds it
 * @param kind       whether it is bought or rented
 * @param price      the price, or the rent per period
 * @param periodDays days a rent pays for, 0 for a purchase
 * @param keeper     who holds the area for the server and gets it back, null for nobody
 * @param created    when it was offered
 */
public record AreaOffer(
        int id,
        String provider,
        String ref,
        String world,
        UUID node,
        Kind kind,
        double price,
        int periodDays,
        @Nullable UUID keeper,
        LocalDateTime created) {
    /**
     * How a company gets an offered area.
     */
    public enum Kind {
        /**
         * Bought once; the company keeps it until it sells it back or disbands.
         */
        BUY,
        /**
         * Rented per period, paid from the treasury ahead.
         */
        RENT;

        /**
         * @return how the company holds an area it got through an offer of this kind
         */
        public AreaSource source() {
            return this == BUY ? AreaSource.BOUGHT : AreaSource.RENTED;
        }
    }
}
