/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.offer;

/**
 * Where an event offer is in its life. The first three are running states, the others are final.
 */
public enum EventOfferState {
    /**
     * Waiting for its start.
     */
    SCHEDULED,
    /**
     * Running.
     */
    ACTIVE,
    /**
     * Stopped by an admin until resumed.
     */
    PAUSED,
    /**
     * Its whole amount was delivered.
     */
    REACHED,
    /**
     * Its end passed before it was reached.
     */
    ENDED,
    /**
     * Its budget could not pay what was still missing.
     */
    DEPLETED,
    /**
     * Closed by an admin or with its server event.
     */
    CANCELLED;

    /**
     * Whether the offer is closed for good.
     *
     * @return true for a final state
     */
    public boolean closed() {
        return this != SCHEDULED && this != ACTIVE && this != PAUSED;
    }
}
