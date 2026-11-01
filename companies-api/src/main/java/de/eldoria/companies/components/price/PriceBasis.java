/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.price;

/**
 * What a shop baseline price was worked out from, the most trusted first.
 */
public enum PriceBasis {
    /**
     * Money that changed hands in shops.
     */
    TRADES,
    /**
     * Prices players ask in their shops.
     */
    LISTINGS,
    /**
     * Prices admins set for the server.
     */
    SERVER
}
