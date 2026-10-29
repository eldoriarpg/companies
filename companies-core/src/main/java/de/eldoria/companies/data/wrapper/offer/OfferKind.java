/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.offer;

/**
 * What an event offer asks of the players.
 */
public enum OfferKind {
    /**
     * A large amount split into ordinary orders, the chunks, that every company or solo worker can take.
     */
    GOAL,
    /**
     * One offer several companies join and deliver into at once, paid per item. Not built yet; reserved so contests
     * share the table and the money of goals.
     */
    CONTEST
}
