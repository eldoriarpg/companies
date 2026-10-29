/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.offer;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where an event offer is in its life. The first three are running states, the others are final: a closed offer
 * places nothing anymore, its open chunks are withdrawn, and chunks already taken may still be delivered.
 */
public enum OfferState {
    /**
     * Waiting for its start.
     */
    SCHEDULED,
    /**
     * Running: its chunks are placed and taken.
     */
    ACTIVE,
    /**
     * Stopped by an admin until resumed; its open chunks are withdrawn.
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
     * Closed by an admin.
     */
    CANCELLED;

    /**
     * The states an offer can still change from on its own or by an admin.
     */
    public static final Set<OfferState> RUNNING = EnumSet.of(SCHEDULED, ACTIVE, PAUSED);

    /**
     * @return whether the offer is closed for good
     */
    public boolean closed() {
        return !RUNNING.contains(this);
    }
}
