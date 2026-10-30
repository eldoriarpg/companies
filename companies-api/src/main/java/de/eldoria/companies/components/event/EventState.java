/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.event;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where a server event is in its life. The first two are running states, the others are final.
 */
public enum EventState {
    /**
     * Waiting for its start.
     */
    SCHEDULED,
    /**
     * Running: its effects apply.
     */
    ACTIVE,
    /**
     * Its end passed, or an admin ended it early. A scheduled event whose whole time passed while the primary server
     * was down ends without starting, notes {@code #missed} as who closed it, and is not announced.
     */
    ENDED,
    /**
     * Its funding could not pay its part of a payout anymore.
     */
    DEPLETED,
    /**
     * Cancelled by an admin, before or while it ran. Only one that ran is announced as cancelled. One withdrawn before
     * its start because its template was disabled or removed notes {@code #template} as who closed it.
     */
    CANCELLED;

    /**
     * The states an event can still change from.
     */
    public static final Set<EventState> RUNNING = EnumSet.of(SCHEDULED, ACTIVE);

    /**
     * Whether the event is over for good.
     *
     * @return whether the event is over for good
     */
    public boolean closed() {
        return !RUNNING.contains(this);
    }
}
