/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.area;

/**
 * How a company gets an area the server offers.
 */
public enum OfferKind {
    /**
     * Bought once; the company keeps it until it sells it back or disbands.
     */
    BUY,
    /**
     * Rented per period, paid from the treasury ahead.
     */
    RENT;

    /**
     * How the company holds an area it got through an offer of this kind.
     *
     * @return how the company holds an area it got through an offer of this kind
     */
    public AreaSource source() {
        return this == BUY ? AreaSource.BOUGHT : AreaSource.RENTED;
    }
}
