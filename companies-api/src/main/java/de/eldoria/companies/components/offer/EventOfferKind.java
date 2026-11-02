/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.offer;

/**
 * What an event offer is.
 */
public enum EventOfferKind {
    /**
     * A shared goal: the server places its amount as system orders in chunks, which any company or player takes.
     */
    GOAL,
    /**
     * A contest: companies join and deliver into it, are paid per item, and the first places win prizes.
     */
    CONTEST
}
